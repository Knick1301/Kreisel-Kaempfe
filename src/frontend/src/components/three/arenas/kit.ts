import {
  AdditiveBlending,
  BufferGeometry,
  CanvasTexture,
  Color,
  DoubleSide,
  Euler,
  Float32BufferAttribute,
  Group,
  Matrix4,
  Mesh,
  MeshBasicMaterial,
  MeshPhysicalMaterial,
  PointLight,
  Quaternion,
  SRGBColorSpace,
  Vector3,
  type Material,
  type Texture,
} from 'three'
import { mergeGeometries } from 'three/examples/jsm/utils/BufferGeometryUtils.js'
import { bowlY, type ArenaDefinition, type ArenaPalette } from '@/game/arenas'
import { revolve } from '../battle/arenaGeometry'

/**
 * Baukasten für die 3D-Arenen: Materialien im Stil der Kreisel-Modelle (Chrom, Gold, leuchtender
 * Pastell-Kunststoff, Kristall), Boden-Texturen aus dem Canvas und Sammel-Eimer, die alle Teile eines
 * Materials zu einem Mesh zusammenfassen (wenige Draw-Calls). `dispose()` räumt alles wieder ab.
 */

export interface BuiltArena {
  group: Group
  dispose(): void
}

type Disposable = { dispose(): void }

export class ArenaKit {
  readonly group = new Group()
  private readonly owned: Disposable[] = []
  private readonly buckets = new Map<Material, BufferGeometry[]>()

  constructor(
    readonly arena: ArenaDefinition,
    readonly palette: ArenaPalette,
  ) {}

  own<T extends Disposable>(x: T): T {
    this.owned.push(x)
    return x
  }

  // ---------------------------------------------------------------- Materialien

  /** Poliertes Metall (Chrom, Stahl). */
  chrome(color = this.palette.metal, roughness = 0.16, envMapIntensity = 1.25) {
    return this.own(
      new MeshPhysicalMaterial({
        color,
        metalness: 1,
        roughness,
        clearcoat: 0.6,
        clearcoatRoughness: 0.15,
        envMapIntensity,
      }),
    )
  }

  /** Zierteile: Gold, Chrom-Kanten. */
  trim(color = this.palette.trim) {
    return this.own(
      new MeshPhysicalMaterial({
        color,
        metalness: 1,
        roughness: 0.22,
        clearcoat: 0.8,
        envMapIntensity: 1.4,
      }),
    )
  }

  /** Durchscheinender, leicht leuchtender Kunststoff wie bei den Kreisel-Ebenen. */
  plastic(color = this.palette.accent, glow = 0.45, opacity = 0.82, envMapIntensity = 1) {
    return this.own(
      new MeshPhysicalMaterial({
        color,
        emissive: new Color(color),
        emissiveIntensity: glow,
        metalness: 0,
        roughness: 0.2,
        clearcoat: 1,
        clearcoatRoughness: 0.08,
        envMapIntensity,
        transparent: opacity < 1,
        opacity,
        side: DoubleSide,
      }),
    )
  }

  /** Facettierter Kristall. */
  crystal(color = this.palette.accent, glow = 0.55, opacity = 0.78) {
    return this.own(
      new MeshPhysicalMaterial({
        color,
        emissive: new Color(color),
        emissiveIntensity: glow,
        metalness: 0.1,
        roughness: 0.05,
        clearcoat: 1,
        transparent: true,
        opacity,
        flatShading: true,
        envMapIntensity: 1.6,
      }),
    )
  }

  /** Selbstleuchtend, ohne Licht (Lichtleisten, Taschenböden). */
  glow(color = this.palette.accent, opacity = 1) {
    return this.own(
      new MeshBasicMaterial({
        color,
        transparent: opacity < 1,
        opacity,
        side: DoubleSide,
        toneMapped: false,
      }),
    )
  }

  /** Weiches Zusatzleuchten (additiv), z. B. Lichthof unter Leisten. */
  halo(texture: Texture, color = this.palette.accent, opacity = 0.6) {
    return this.own(
      new MeshBasicMaterial({
        map: texture,
        color,
        transparent: true,
        opacity,
        blending: AdditiveBlending,
        depthWrite: false,
        side: DoubleSide,
        toneMapped: false,
      }),
    )
  }

  /** Bodenmaterial mit Textur, Glanz aus der Palette. */
  floor(map: Texture, metalness = 0.55) {
    const g = this.palette.gloss
    return this.own(
      new MeshPhysicalMaterial({
        map,
        metalness,
        roughness: 0.55 - 0.47 * g,
        clearcoat: g,
        clearcoatRoughness: 0.1,
        envMapIntensity: 0.6 + 0.6 * g,
        side: DoubleSide,
      }),
    )
  }

  // ---------------------------------------------------------------- Geometrie sammeln

  /** Geometrie in den Eimer des Materials legen, wird beim `finish()` zusammengeführt. */
  add(material: Material, geometry: BufferGeometry) {
    const list = this.buckets.get(material) ?? []
    list.push(geometry)
    this.buckets.set(material, list)
  }

  /** Einzelnes Mesh direkt (z. B. mit eigener Render-Reihenfolge). */
  mesh(material: Material, geometry: BufferGeometry, renderOrder = 0) {
    const m = new Mesh(this.own(geometry), material)
    m.renderOrder = renderOrder
    this.group.add(m)
    return m
  }

  light(color: string, intensity: number, x: number, y: number, z: number, distance = 5) {
    const l = new PointLight(color, intensity, distance, 2)
    l.position.set(x, y, z)
    this.group.add(l)
    return l
  }

  finish(): BuiltArena {
    for (const [material, list] of this.buckets) {
      const merged = mergeAll(list)
      list.forEach((g) => g.dispose())
      if (!merged) continue
      const m = new Mesh(this.own(merged), material)
      // durchscheinende Teile nach dem Boden zeichnen
      if ((material as MeshPhysicalMaterial).transparent) m.renderOrder = 1
      this.group.add(m)
    }
    this.buckets.clear()
    return {
      group: this.group,
      dispose: () => {
        this.owned.forEach((x) => x.dispose())
        this.owned.length = 0
      },
    }
  }

  // ---------------------------------------------------------------- Boden

  /** Bowl als Rotationsfläche mit planaren UVs (Textur von oben, Mitte = 0,5/0,5). */
  bowlSurface(radius: number, size: number, rings = 80, segments = 192) {
    const a = this.arena
    const profile: [number, number][] = Array.from({ length: rings + 1 }, (_, i) => {
      const r = (i / rings) * radius
      return [r, bowlY(a, Math.min(r, a.radius))]
    })
    return planarUV(revolve(profile, 0, 2 * Math.PI, segments), size)
  }
}

/** Planare UVs von oben: u = x/size + 0,5, v = z/size + 0,5. */
export function planarUV(g: BufferGeometry, size: number) {
  const pos = g.attributes.position!
  const uv = new Float32Array(pos.count * 2)
  for (let i = 0; i < pos.count; i++) {
    uv[i * 2] = pos.getX(i) / size + 0.5
    uv[i * 2 + 1] = pos.getZ(i) / size + 0.5
  }
  g.setAttribute('uv', new Float32BufferAttribute(uv, 2))
  return g
}

/** Geometrien zusammenführen, auch wenn manche indiziert sind und andere nicht. */
export function mergeAll(list: BufferGeometry[]) {
  if (list.length === 0) return null
  const prepared = list.map((g) => {
    const flat = g.index ? g.toNonIndexed() : g.clone()
    for (const name of Object.keys(flat.attributes)) {
      if (name !== 'position' && name !== 'normal' && name !== 'uv') flat.deleteAttribute(name)
    }
    if (!flat.attributes.normal) flat.computeVertexNormals()
    if (!flat.attributes.uv) {
      flat.setAttribute(
        'uv',
        new Float32BufferAttribute(new Float32Array(flat.attributes.position!.count * 2), 2),
      )
    }
    return flat
  })
  const merged = mergeGeometries(prepared, false)
  prepared.forEach((g) => g.dispose())
  return merged
}

// ---------------------------------------------------------------- Canvas-Texturen

/** Quadratische Canvas-Textur, gezeichnet in Weltkoordinaten: (0, 0) ist die Mitte, `size` die Kantenlänge. */
export function drawTexture(
  size: number,
  draw: (g: CanvasRenderingContext2D, scale: number) => void,
  px = 2048,
) {
  const c = document.createElement('canvas')
  c.width = c.height = px
  const g = c.getContext('2d')!
  const scale = px / size
  g.translate(px / 2, px / 2)
  draw(g, scale)
  const tex = new CanvasTexture(c)
  tex.colorSpace = SRGBColorSpace
  tex.anisotropy = 8
  return tex
}

/** Radialer Verlauf aus 5 Stopps von der Mitte bis `radius` (Welt). */
export function radialFloor(
  g: CanvasRenderingContext2D,
  scale: number,
  radius: number,
  stops: readonly string[],
) {
  const grad = g.createRadialGradient(0, 0, 0, 0, 0, radius * scale)
  const at = [0, 0.2, 0.48, 0.85, 1]
  stops.forEach((c, i) => grad.addColorStop(at[i] ?? 1, c))
  g.fillStyle = grad
  g.fillRect(-g.canvas.width, -g.canvas.height, g.canvas.width * 2, g.canvas.height * 2)
}

/** Weicher runder Lichtfleck (für Halos). */
export function makeHaloTexture() {
  const c = document.createElement('canvas')
  c.width = c.height = 128
  const g = c.getContext('2d')!
  const grad = g.createRadialGradient(64, 64, 0, 64, 64, 64)
  grad.addColorStop(0, 'rgba(255,255,255,1)')
  grad.addColorStop(0.35, 'rgba(255,255,255,0.35)')
  grad.addColorStop(1, 'rgba(255,255,255,0)')
  g.fillStyle = grad
  g.fillRect(0, 0, 128, 128)
  return new CanvasTexture(c)
}

/** Logo der App mittig in eine Textur zeichnen, sobald es geladen ist. */
export function drawLogoInto(
  tex: CanvasTexture,
  worldSize: number,
  logoSize: number,
  alpha: number,
) {
  const img = new Image()
  img.onload = () => {
    const c = tex.image as HTMLCanvasElement
    const g = c.getContext('2d')!
    const px = (logoSize / worldSize) * c.width
    g.save()
    g.globalAlpha = alpha
    g.globalCompositeOperation = 'screen'
    g.drawImage(img, c.width / 2 - px / 2, c.height / 2 - px / 2, px, px)
    g.restore()
    tex.needsUpdate = true
  }
  img.src = '/logokreisel.png'
}

export function hexToRgba(hex: string, alpha: number) {
  const n = parseInt(hex.replace('#', ''), 16)
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${alpha})`
}

const tmpMatrix = new Matrix4()
const tmpQuat = new Quaternion()
const tmpEuler = new Euler()
const tmpPos = new Vector3()
const ONE = new Vector3(1, 1, 1)

/** Geometrie verschieben und drehen (Euler XYZ in rad), gibt sie zurück. */
export function place(
  g: BufferGeometry,
  x: number,
  y: number,
  z: number,
  rx = 0,
  ry = 0,
  rz = 0,
  order: 'XYZ' | 'YXZ' | 'ZXY' = 'XYZ',
) {
  tmpEuler.set(rx, ry, rz, order)
  tmpQuat.setFromEuler(tmpEuler)
  tmpMatrix.compose(tmpPos.set(x, y, z), tmpQuat, ONE)
  g.applyMatrix4(tmpMatrix)
  return g
}

/**
 * Lokales Koordinatensystem an einer Wand mit Normale in Richtung θ (Polarwinkel): `along` läuft tangential,
 * `out` nach außen. Liefert Weltposition und die Y-Drehung, bei der lokales +x tangential und +z nach innen zeigt.
 */
export function wallFrame(theta: number) {
  const nx = Math.cos(theta)
  const nz = Math.sin(theta)
  return {
    rotY: -theta - Math.PI / 2,
    at(out: number, along: number) {
      return { x: nx * out - nz * along, z: nz * out + nx * along }
    },
  }
}

/** Dreiecke einer nicht indizierten Geometrie so drehen, dass ihre Normalen nach oben zeigen. */
export function faceUp(g: BufferGeometry) {
  const pos = g.attributes.position!
  const arr = pos.array as Float32Array
  for (let i = 0; i < pos.count; i += 3) {
    const ax = arr[i * 3]!
    const az = arr[i * 3 + 2]!
    const bx = arr[(i + 1) * 3]!
    const bz = arr[(i + 1) * 3 + 2]!
    const cx = arr[(i + 2) * 3]!
    const cz = arr[(i + 2) * 3 + 2]!
    // y-Komponente des Kreuzprodukts (b − a) × (c − a)
    const ny = (bz - az) * (cx - ax) - (bx - ax) * (cz - az)
    if (ny < 0) {
      for (let k = 0; k < 3; k++) {
        const t = arr[(i + 1) * 3 + k]!
        arr[(i + 1) * 3 + k] = arr[(i + 2) * 3 + k]!
        arr[(i + 2) * 3 + k] = t
      }
    }
  }
  pos.needsUpdate = true
  g.computeVertexNormals()
  return g
}
