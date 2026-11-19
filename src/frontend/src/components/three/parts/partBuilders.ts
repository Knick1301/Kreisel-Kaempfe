import {
  BufferGeometry,
  Color,
  CylinderGeometry,
  CatmullRomCurve3,
  ExtrudeGeometry,
  Group,
  LatheGeometry,
  Material,
  Mesh,
  MeshPhysicalMaterial,
  MeshStandardMaterial,
  Path,
  Shape,
  SphereGeometry,
  ConeGeometry,
  TorusGeometry,
  TubeGeometry,
  Vector2,
  Vector3,
} from 'three'
import { mergeGeometries } from 'three/examples/jsm/utils/BufferGeometryUtils.js'
import type { TipDesign, TrackDesign, WheelDesign } from '@/game/partDesigns'

/**
 * Prozedurale, detaillierte Kreisel-Teile. Modell-Einheiten: Durchmesser des Fusion Wheels ≈ 1,9,
 * jede Teil-Gruppe hat ihren Ursprung in der eigenen Mitte (Drehpunkt für den Burst).
 */

export type MaterialKind = 'metal' | 'dark' | 'layer' | 'glow' | 'rubber' | 'chrome'

/** Teilt Materialien zwischen Teilen und gibt sie gesammelt frei. */
export class MaterialCache {
  private readonly cache = new Map<string, Material>()

  get(kind: MaterialKind, color: string): Material {
    const key = `${kind}|${color}`
    let m = this.cache.get(key)
    if (!m) {
      m = create(kind, color)
      this.cache.set(key, m)
    }
    return m
  }

  dispose() {
    this.cache.forEach((m) => m.dispose())
    this.cache.clear()
  }
}

function create(kind: MaterialKind, color: string): Material {
  switch (kind) {
    case 'metal':
      return new MeshStandardMaterial({ color, metalness: 1, roughness: 0.26 })
    case 'chrome':
      return new MeshStandardMaterial({ color, metalness: 1, roughness: 0.12 })
    case 'dark':
      return new MeshStandardMaterial({ color, metalness: 0.65, roughness: 0.38 })
    case 'rubber':
      return new MeshStandardMaterial({ color, metalness: 0.05, roughness: 0.82 })
    case 'layer':
      return new MeshPhysicalMaterial({
        color,
        metalness: 0.1,
        roughness: 0.22,
        clearcoat: 1,
        clearcoatRoughness: 0.08,
        transparent: true,
        opacity: 0.88,
        emissive: new Color(color).multiplyScalar(0.25),
      })
    case 'glow':
      return new MeshStandardMaterial({
        color,
        emissive: color,
        emissiveIntensity: 1.6,
        metalness: 0.2,
        roughness: 0.4,
        toneMapped: false,
      })
  }
}

/** Ein gebautes Teil: Gruppe plus alle Geometrien, die beim Austausch freigegeben werden müssen. */
export interface BuiltPart {
  group: Group
  /** Höhe des Teils in Modell-Einheiten */
  height: number
  dispose(): void
}

class Builder {
  readonly group = new Group()
  private readonly geometries: BufferGeometry[] = []

  constructor(private readonly mats: MaterialCache) {}

  add(geometry: BufferGeometry, kind: MaterialKind, color: string, y = 0) {
    this.geometries.push(geometry)
    const mesh = new Mesh(geometry, this.mats.get(kind, color))
    mesh.position.y = y
    this.group.add(mesh)
    return mesh
  }

  done(height: number): BuiltPart {
    const geometries = this.geometries
    return {
      group: this.group,
      height,
      dispose: () => geometries.forEach((g) => g.dispose()),
    }
  }
}

/** Shape in der XZ-Ebene extrudieren (nach oben), vertikal zentriert. */
function extrude(shape: Shape, depth: number, bevel = 0.012, curveSegments = 24) {
  const g = new ExtrudeGeometry(shape, {
    depth,
    bevelEnabled: bevel > 0,
    bevelThickness: bevel,
    bevelSize: bevel,
    bevelSegments: 2,
    curveSegments,
  })
  g.rotateX(-Math.PI / 2)
  g.translate(0, -depth / 2, 0)
  return g
}

/** Eine Geometrie n-mal rund um die Achse verteilt, als eine Geometrie (ein Draw-Call). */
function radial(make: () => BufferGeometry, count: number, offset = 0) {
  const copies: BufferGeometry[] = []
  for (let i = 0; i < count; i++) {
    const g = make()
    g.rotateY(offset + (i * 2 * Math.PI) / count)
    copies.push(g)
  }
  const merged = mergeGeometries(copies)!
  copies.forEach((g) => g.dispose())
  return merged
}

function circlePath(r: number, cx = 0, cy = 0) {
  const p = new Path()
  p.absarc(cx, cy, r, 0, Math.PI * 2, true)
  return p
}

/** Flache Scheibe als Lathe mit Fase (wirkt hochwertiger als ein glatter Zylinder). */
function chamferedDisc(radius: number, height: number, chamfer: number) {
  const h = height / 2
  return new LatheGeometry(
    [
      new Vector2(0, -h),
      new Vector2(radius - chamfer, -h),
      new Vector2(radius, -h + chamfer),
      new Vector2(radius, h - chamfer),
      new Vector2(radius - chamfer, h),
      new Vector2(0, h),
    ],
    64,
  )
}

/** Zahnrad-Kontur einer Ratsche (Sägezähne, schräg wie beim echten Track). */
function ratchetShape(teeth: number, inner: number, outer: number, hole: number) {
  const s = new Shape()
  for (let i = 0; i < teeth; i++) {
    const a0 = (i / teeth) * Math.PI * 2
    const a1 = ((i + 0.62) / teeth) * Math.PI * 2
    const a2 = ((i + 0.8) / teeth) * Math.PI * 2
    const p0 = new Vector2(Math.cos(a0) * inner, Math.sin(a0) * inner)
    if (i === 0) s.moveTo(p0.x, p0.y)
    else s.lineTo(p0.x, p0.y)
    s.lineTo(Math.cos(a1) * outer, Math.sin(a1) * outer)
    s.lineTo(Math.cos(a2) * outer * 0.97, Math.sin(a2) * outer * 0.97)
  }
  s.closePath()
  if (hole > 0) s.holes.push(circlePath(hole))
  return s
}

// ---------------------------------------------------------------- Fusion Wheel

function bladeShape(shape: WheelDesign['shape']): Shape {
  const s = new Shape()
  switch (shape) {
    case 'scythe':
      s.moveTo(0.5, -0.12)
      s.quadraticCurveTo(0.9, -0.12, 0.99, 0.3)
      s.quadraticCurveTo(0.93, 0.2, 0.82, 0.16)
      s.quadraticCurveTo(0.68, 0.12, 0.5, 0.16)
      break
    case 'fang':
    case 'feather':
      s.moveTo(0.5, -0.17)
      s.quadraticCurveTo(0.8, -0.14, 0.99, 0.02)
      s.lineTo(0.86, 0.06)
      s.lineTo(0.93, 0.15)
      s.quadraticCurveTo(0.72, 0.18, 0.5, 0.17)
      break
    case 'fortress':
      s.moveTo(0.5, -0.2)
      s.lineTo(0.74, -0.2)
      s.absarc(0.74, 0, 0.2, -Math.PI / 2, Math.PI / 2, false)
      s.lineTo(0.5, 0.2)
      break
    case 'vortex':
      s.moveTo(0.48, -0.06)
      s.quadraticCurveTo(0.8, -0.28, 0.97, 0.06)
      s.lineTo(0.92, 0.15)
      s.quadraticCurveTo(0.76, -0.1, 0.48, 0.09)
      break
    case 'moon':
      s.absarc(0, 0, 0.97, -0.95, 0.95, false)
      s.absarc(0.3, 0, 0.62, 0.95, -0.95, true)
      break
    case 'phantom':
      s.moveTo(0.46, -0.05)
      s.bezierCurveTo(0.7, -0.32, 0.96, -0.22, 0.99, 0.06)
      s.lineTo(0.9, 0.09)
      s.bezierCurveTo(0.8, -0.08, 0.64, -0.02, 0.46, 0.09)
      break
  }
  s.closePath()
  return s
}

function wheelRingShape(d: WheelDesign) {
  const s = new Shape()
  const steps = d.notches * 8
  for (let i = 0; i <= steps; i++) {
    const a = (i / steps) * Math.PI * 2
    // Kerben: kurze Einschnitte im Außenrand
    const phase = (i % 8) / 8
    const r = phase < 0.18 ? 0.71 : 0.78
    if (i === 0) s.moveTo(Math.cos(a) * r, Math.sin(a) * r)
    else s.lineTo(Math.cos(a) * r, Math.sin(a) * r)
  }
  s.holes.push(circlePath(0.42))
  return s
}

function layerShape(d: WheelDesign) {
  const s = new Shape()
  s.absarc(0, 0, 0.74, 0, Math.PI * 2, false)
  // nierenförmige Aussparungen zwischen den Klingen
  const n = Math.max(3, d.blades)
  for (let i = 0; i < n; i++) {
    const a = ((i + 0.5) / n) * Math.PI * 2
    s.holes.push(circlePath(0.07, Math.cos(a) * 0.58, Math.sin(a) * 0.58))
  }
  s.holes.push(circlePath(0.3))
  return s
}

function emblemShape(points: number) {
  const s = new Shape()
  for (let i = 0; i < points * 2; i++) {
    const a = (i / (points * 2)) * Math.PI * 2 + Math.PI / 2
    const r = i % 2 === 0 ? 0.15 : 0.06
    if (i === 0) s.moveTo(Math.cos(a) * r, Math.sin(a) * r)
    else s.lineTo(Math.cos(a) * r, Math.sin(a) * r)
  }
  s.closePath()
  return s
}

export function buildWheel(d: WheelDesign, mats: MaterialCache): BuiltPart {
  const b = new Builder(mats)
  const ring = 0.1 * d.heft

  // farbige Kunststoff-Ebene
  b.add(extrude(layerShape(d), 0.05, 0.008), 'layer', d.layer, -0.07)
  // Metallring mit Kerben
  b.add(extrude(wheelRingShape(d), ring, 0.014, 12), 'metal', d.metal, -0.01)
  // Klingen, gleichmäßig verteilt
  b.add(
    radial(() => extrude(bladeShape(d.shape), 0.075, 0.016), d.blades),
    'metal',
    d.metal,
    0.055,
  )
  // Leuchtkanten auf den Klingen
  b.add(
    radial(() => {
      const g = extrude(bladeShape(d.shape), 0.01, 0)
      g.scale(0.97, 1, 0.97)
      return g
    }, d.blades),
    'glow',
    d.accent,
    0.115,
  )
  // Leuchtring
  const glowRing = new TorusGeometry(0.6, 0.011, 8, 120)
  glowRing.rotateX(Math.PI / 2)
  b.add(glowRing, 'glow', d.accent, 0.04)
  // Nabe mit Fase, Emblem und Schrauben
  b.add(chamferedDisc(0.36, 0.2, 0.05), 'dark', '#1d1f24', 0.06)
  b.add(chamferedDisc(0.24, 0.05, 0.015), 'chrome', '#e8ebf0', 0.17)
  b.add(
    extrude(emblemShape(d.blades >= 5 ? 5 : d.blades + 2), 0.03, 0.006),
    'glow',
    d.accent,
    0.205,
  )
  b.add(
    radial(
      () => {
        const g = new CylinderGeometry(0.028, 0.028, 0.03, 12)
        g.translate(0.29, 0, 0)
        return g
      },
      3,
      Math.PI / 6,
    ),
    'chrome',
    '#f2f4f7',
    0.165,
  )
  return b.done(0.32)
}

// ---------------------------------------------------------------- Spin Track

const TRACK_HEIGHT: Record<TrackDesign['style'], number> = {
  heavy: 0.24,
  orbit: 0.34,
  core: 0.22,
  tower: 0.4,
  bastion: 0.26,
  spring: 0.36,
  chalice: 0.3,
}

export function buildTrack(d: TrackDesign, mats: MaterialCache): BuiltPart {
  const b = new Builder(mats)
  const h = TRACK_HEIGHT[d.style]
  const top = h / 2
  const gear = () => extrude(ratchetShape(d.teeth, 0.36, 0.45, 0.14), 0.07, 0.01, 6)

  // Schaft durch die Mitte
  b.add(new CylinderGeometry(0.16, 0.18, h, 32), 'dark', '#2a2d33', 0)

  switch (d.style) {
    case 'heavy': {
      b.add(chamferedDisc(0.66, 0.1, 0.03), 'metal', d.metal, -top + 0.07)
      b.add(gear(), 'metal', d.metal, top - 0.05)
      break
    }
    case 'orbit': {
      const ring = new TorusGeometry(0.38, 0.045, 16, 96)
      ring.rotateX(Math.PI / 2)
      b.add(ring, 'chrome', d.metal, -0.03)
      b.add(
        radial(() => {
          const g = new CylinderGeometry(0.02, 0.02, 0.24, 8)
          g.rotateZ(Math.PI / 2)
          g.translate(0.26, 0, 0)
          return g
        }, 3),
        'dark',
        '#3a3e46',
        -0.03,
      )
      b.add(gear(), 'metal', d.metal, top - 0.05)
      break
    }
    case 'core': {
      b.add(
        extrude(ratchetShape(d.teeth, 0.33, 0.42, 0.14), 0.07, 0.01, 6),
        'metal',
        d.metal,
        -0.05,
      )
      b.add(gear(), 'metal', d.metal, top - 0.05)
      break
    }
    case 'tower':
    case 'chalice': {
      for (let i = 0; i < 3; i++) {
        const ring = new TorusGeometry(0.22, 0.03, 12, 64)
        ring.rotateX(Math.PI / 2)
        b.add(ring, 'chrome', d.metal, -top + 0.07 + i * 0.09)
      }
      b.add(gear(), 'metal', d.metal, top - 0.05)
      break
    }
    case 'bastion': {
      b.add(new CylinderGeometry(0.52, 0.56, 0.14, 6), 'metal', d.metal, -0.03)
      b.add(
        radial(
          () => {
            const g = new CylinderGeometry(0.035, 0.035, 0.05, 10)
            g.translate(0.42, 0, 0)
            return g
          },
          6,
          Math.PI / 6,
        ),
        'chrome',
        '#eef0f3',
        0.055,
      )
      b.add(gear(), 'metal', d.metal, top - 0.05)
      break
    }
    case 'spring': {
      const turns = 3.5
      const points = Array.from({ length: 80 }, (_, i) => {
        const u = i / 79
        const a = u * turns * Math.PI * 2
        return new Vector3(Math.cos(a) * 0.25, -top + 0.05 + u * (h - 0.14), Math.sin(a) * 0.25)
      })
      b.add(new TubeGeometry(new CatmullRomCurve3(points), 160, 0.025, 8), 'chrome', d.metal, 0)
      b.add(gear(), 'metal', d.metal, top - 0.05)
      break
    }
  }
  const glow = new TorusGeometry(0.34, 0.009, 8, 96)
  glow.rotateX(Math.PI / 2)
  b.add(glow, 'glow', d.accent, top - 0.1)
  return b.done(h)
}

// ---------------------------------------------------------------- Tip

export function buildTip(d: TipDesign, mats: MaterialCache): BuiltPart {
  const b = new Builder(mats)
  // Gehäuse
  b.add(
    new LatheGeometry(
      [
        new Vector2(0, 0.15),
        new Vector2(0.27, 0.15),
        new Vector2(0.29, 0.12),
        new Vector2(0.25, 0.04),
        new Vector2(0.18, -0.03),
        new Vector2(0, -0.03),
      ],
      48,
    ),
    'dark',
    '#20232a',
  )
  const band = new TorusGeometry(0.255, 0.012, 8, 72)
  band.rotateX(Math.PI / 2)
  b.add(band, 'glow', d.accent, 0.09)

  switch (d.style) {
    case 'flat':
      b.add(chamferedDisc(0.15, 0.1, 0.02), 'rubber', d.color, -0.08)
      break
    case 'needle':
    case 'spear': {
      const g = new ConeGeometry(0.09, 0.13, 32)
      g.rotateX(Math.PI)
      b.add(g, 'chrome', d.color, -0.095)
      break
    }
    case 'ball':
      b.add(new SphereGeometry(0.11, 32, 16), 'chrome', d.color, -0.07)
      break
    case 'claw':
      b.add(chamferedDisc(0.16, 0.08, 0.02), 'rubber', d.color, -0.07)
      b.add(
        radial(() => {
          const g = new CylinderGeometry(0.03, 0.045, 0.07, 6)
          g.translate(0.16, 0, 0)
          return g
        }, 4),
        'rubber',
        d.color,
        -0.09,
      )
      break
    case 'bearing': {
      b.add(
        radial(() => {
          const g = new SphereGeometry(0.03, 12, 8)
          g.translate(0.13, 0, 0)
          return g
        }, 9),
        'chrome',
        '#f5f6f8',
        -0.04,
      )
      const g = new ConeGeometry(0.07, 0.11, 24)
      g.rotateX(Math.PI)
      b.add(g, 'chrome', d.color, -0.09)
      break
    }
    case 'dome': {
      const g = new SphereGeometry(0.17, 32, 12, 0, Math.PI * 2, Math.PI / 2, Math.PI / 2)
      b.add(g, 'dark', d.color, -0.03)
      break
    }
  }
  return b.done(0.3)
}

/** Ruhehöhen der Teile im Rotor (Mitte jedes Teils), Spitze unten bei y = 0. */
export function stackHeights(track: TrackDesign) {
  const tipH = 0.3
  const trackH = TRACK_HEIGHT[track.style]
  const tipY = 0.15 + 0.03
  const trackY = tipH + trackH / 2
  const wheelY = tipH + trackH + 0.1
  return { tipY, trackY, wheelY, total: wheelY + 0.22 }
}
