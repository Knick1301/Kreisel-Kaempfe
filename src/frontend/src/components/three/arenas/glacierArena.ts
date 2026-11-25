import {
  BufferGeometry,
  CanvasTexture,
  CylinderGeometry,
  Float32BufferAttribute,
  PlaneGeometry,
  SRGBColorSpace,
} from 'three'
import {
  bowlY,
  pocketFloorY,
  rimSegments,
  rimY,
  type ArenaDefinition,
  type ArenaVariant,
} from '@/game/arenas'
import { mulberry32 } from '@/game/prng'
import { revolve } from '../battle/arenaGeometry'
import { mergeGeometries } from 'three/examples/jsm/utils/BufferGeometryUtils.js'
import {
  ArenaKit,
  drawLogoInto,
  drawTexture,
  hexToRgba,
  makeHaloTexture,
  place,
  radialFloor,
  type BuiltArena,
} from './kit'

/**
 * Gletscher: weite, spiegelglatte Eisfläche mit Frostrissen und Schneeflocken-Emblem. Ein eisiger Randwulst,
 * davor ein Kranz aus facettierten Kristall-Clustern, zwei breite Gletscherspalten als Taschen. Im Polarlicht
 * hängen leuchtende Vorhänge über der Arena.
 */

export function buildGlacierArena(arena: ArenaDefinition, variant: ArenaVariant): BuiltArena {
  const p = variant.palette
  const kit = new ArenaKit(arena, p)
  const R = arena.radius
  const edge = bowlY(arena, R)
  const rim = rimY(arena)
  const aurora = variant.id === 'aurora'
  const rng = mulberry32(4711 + variant.id.length)

  const ice = kit.crystal(p.metal, aurora ? 0.15 : 0.08, 0.92)
  const frost = kit.chrome(aurora ? '#24343c' : '#e9f1f7', 0.55)
  const crystalA = kit.crystal(p.accent, aurora ? 0.95 : 0.75, 0.85)
  const crystalB = kit.crystal(p.accent2, aurora ? 0.85 : 0.55, 0.8)
  const trim = kit.trim()
  const glowLine = kit.glow(p.accent)
  const pocketMat = kit.glow(p.pocketFloor, 0.85)

  // ---------------------------------------------------------------- Eisfläche
  const size = 2 * (R + 0.2)
  const tex = kit.own(drawTexture(size, (g, s) => drawGlacierFloor(g, s, arena, variant)))
  drawLogoInto(tex, size, 1.6, aurora ? 0.5 : 0.3)
  kit.mesh(kit.floor(tex, 0.15), kit.bowlSurface(R, size, 90, 240))

  // ---------------------------------------------------------------- Randwulst und Kristalle
  for (const [a0, a1] of rimSegments(arena)) {
    const seg = Math.max(12, Math.ceil(((a1 - a0) * 180) / Math.PI))
    kit.add(
      ice,
      revolve(
        [
          [R, edge],
          [R + 0.06, rim + 0.06],
          [R + 0.3, rim + 0.12],
          [R + 0.75, rim + 0.02],
          [R + 1.1, rim - 0.12],
        ],
        a0,
        a1,
        seg,
      ),
    )
    kit.add(
      trim,
      revolve(
        [
          [R + 0.04, rim + 0.045],
          [R + 0.08, rim + 0.07],
        ],
        a0,
        a1,
        seg,
      ),
    )
    kit.add(
      glowLine,
      revolve(
        [
          [R - 0.03, edge + 0.002],
          [R + 0.01, edge + 0.03],
        ],
        a0,
        a1,
        seg,
      ),
    )

    // Kristall-Cluster entlang des Rands
    const step = (7 * Math.PI) / 180
    for (let a = a0 + step / 2; a < a1 - step / 3; a += step) {
      const count = 1 + Math.floor(rng() * 3)
      // zur Kamera hin (vorne, +z) niedriger, hinten hoch: die Arena bleibt frei
      const front = Math.max(0, Math.sin(a))
      const scale = 1 - 0.62 * front
      for (let c = 0; c < count; c++) {
        const ang = a + (rng() - 0.5) * step * 0.8
        const r = R + 0.5 + rng() * 0.45
        const h = (0.3 + rng() * 0.7 + (r - R - 0.5) * 0.8) * scale
        const w = 0.07 + rng() * 0.09
        const g = crystalShard(w, h)
        // nach außen kippen, etwas zufällig
        const tilt = 0.15 + rng() * 0.35
        const twist = (rng() - 0.5) * 0.6
        place(g, 0, 0, 0, 0, rng() * Math.PI)
        place(g, 0, 0, 0, twist, 0, -tilt)
        place(g, Math.cos(ang) * r, rim + 0.02 - 0.08 * (r - R), Math.sin(ang) * r, 0, -ang)
        kit.add(rng() < 0.6 ? crystalA : crystalB, g)
      }
    }
  }

  // ---------------------------------------------------------------- Gletscherspalten
  for (const q of arena.pockets) {
    const a0 = q - arena.pocketHalfWidth
    const a1 = q + arena.pocketHalfWidth
    const floor = pocketFloorY(arena)
    const back = R + arena.pocketDepth
    const seg = 16
    kit.add(
      pocketMat,
      revolve(
        [
          [R, floor],
          [back, floor],
        ],
        a0,
        a1,
        seg,
      ),
    )
    kit.add(
      ice,
      revolve(
        [
          [R, floor],
          [R, edge],
        ],
        a0,
        a1,
        seg,
      ),
    )
    kit.add(
      ice,
      revolve(
        [
          [back, floor],
          [back + 0.1, rim + 0.08],
          [back + 0.4, rim],
        ],
        a0,
        a1,
        seg,
      ),
    )
    for (const a of [a0, a1]) {
      kit.add(ice, radialWall(a, R, back + 0.1, floor, rim + 0.08))
      // große Kristalle an den Spaltenrändern
      for (let i = 0; i < 2; i++) {
        const g = crystalShard(0.16, (1.1 + i * 0.5) * (1 - 0.6 * Math.max(0, Math.sin(a))))
        place(g, 0, 0, 0, 0.2 * (i ? -1 : 1), 0, -0.25)
        const r = R + 0.35 + i * 0.4
        const side = a === a0 ? -0.04 : 0.04
        place(g, Math.cos(a + side) * r, rim, Math.sin(a + side) * r, 0, -(a + side))
        kit.add(crystalA, g)
      }
    }
    kit.light(
      p.pocketFloor,
      aurora ? 5 : 3.5,
      Math.cos(q) * (R + 0.5),
      floor + 0.4,
      Math.sin(q) * (R + 0.5),
      3.5,
    )
  }

  // ---------------------------------------------------------------- Sockel
  kit.add(
    frost,
    place(new CylinderGeometry(R + 1.12, R + 1.4, 2.2, 96, 1, true), 0, rim - 0.12 - 1.1, 0),
  )
  kit.add(
    trim,
    place(new CylinderGeometry(R + 1.125, R + 1.14, 0.04, 96, 1, true), 0, rim - 0.15, 0),
  )

  // ---------------------------------------------------------------- Licht und Polarlicht
  const halo = kit.own(makeHaloTexture())
  kit.mesh(
    kit.halo(halo, p.accent, aurora ? 0.3 : 0.06),
    place(new PlaneGeometry(5, 5), 0, bowlY(arena, 0) + 0.01, 0, -Math.PI / 2),
    2,
  )
  kit.light(p.accent2, aurora ? 2.2 : 0.8, 0, 3.2, 0, 8)
  for (let i = 0; i < 4; i++) {
    const a = (i * Math.PI) / 2 + Math.PI / 4
    kit.light(
      p.accent,
      aurora ? 4 : 2.5,
      Math.cos(a) * (R + 0.7),
      rim + 0.6,
      Math.sin(a) * (R + 0.7),
      4,
    )
  }
  if (aurora) {
    const curtain = kit.own(makeAuroraTexture(p.accent, p.accent2))
    const mat = kit.halo(curtain, '#ffffff', 0.55)
    for (const [start, span, radius, height] of [
      [Math.PI * 1.05, Math.PI * 0.9, R + 4.5, 4.2],
      [Math.PI * 1.25, Math.PI * 0.6, R + 3.2, 2.8],
    ] as const) {
      kit.mesh(mat, curtainGeometry(radius, start, span, rim + 0.6, height), 3)
    }
  }

  return kit.finish()
}

/** Kristall: sechseckiges Prisma mit spitzer Kappe, Fuß im Ursprung, nach +y. */
function crystalShard(width: number, height: number) {
  const body = new CylinderGeometry(width * 0.85, width, height * 0.72, 6)
  place(body, 0, (height * 0.72) / 2, 0)
  const tip = new CylinderGeometry(0, width * 0.85, height * 0.28, 6)
  place(tip, 0, height * 0.72 + (height * 0.28) / 2, 0)
  const merged = mergeGeometries([body.toNonIndexed(), tip.toNonIndexed()])!
  body.dispose()
  tip.dispose()
  merged.computeVertexNormals()
  return merged
}

function radialWall(a: number, r0: number, r1: number, y0: number, y1: number) {
  const c = Math.cos(a)
  const s = Math.sin(a)
  const g = new BufferGeometry()
  g.setAttribute(
    'position',
    new Float32BufferAttribute(
      [r0 * c, y0, r0 * s, r1 * c, y0, r1 * s, r0 * c, y1, r0 * s, r1 * c, y1, r1 * s],
      3,
    ),
  )
  g.setIndex([0, 1, 2, 2, 1, 3])
  g.computeVertexNormals()
  return g
}

/** Gebogener, senkrechter Vorhang (Teil eines Zylindermantels) mit welliger Unterkante. */
function curtainGeometry(radius: number, start: number, span: number, y0: number, height: number) {
  const cols = 64
  const pos: number[] = []
  const uv: number[] = []
  const idx: number[] = []
  for (let i = 0; i <= cols; i++) {
    const u = i / cols
    const a = start + span * u
    const wave = 0.35 * Math.sin(u * Math.PI * 5) + 0.2 * Math.sin(u * Math.PI * 11 + 1)
    const r = radius + 0.5 * Math.sin(u * Math.PI * 3)
    for (const v of [0, 1]) {
      pos.push(Math.cos(a) * r, y0 + wave + v * height, Math.sin(a) * r)
      uv.push(u, v)
    }
  }
  for (let i = 0; i < cols; i++) {
    const a = i * 2
    idx.push(a, a + 2, a + 1, a + 1, a + 2, a + 3)
  }
  const g = new BufferGeometry()
  g.setAttribute('position', new Float32BufferAttribute(pos, 3))
  g.setAttribute('uv', new Float32BufferAttribute(uv, 2))
  g.setIndex(idx)
  g.computeVertexNormals()
  return g
}

/** Polarlicht: senkrechte Lichtbahnen, unten hell, nach oben ausblendend, Farbe wechselt seitlich. */
function makeAuroraTexture(a: string, b: string) {
  const c = document.createElement('canvas')
  c.width = 512
  c.height = 256
  const g = c.getContext('2d')!
  const rng = mulberry32(99)
  for (let x = 0; x < 512; x += 2) {
    const u = x / 512
    const strength = 0.35 + 0.65 * Math.abs(Math.sin(u * Math.PI * 4 + rng() * 0.6))
    const grad = g.createLinearGradient(0, 256, 0, 0)
    const color = u < 0.5 ? a : b
    grad.addColorStop(0, hexToRgba(color, 0))
    grad.addColorStop(0.08, hexToRgba(color, 0.9 * strength))
    grad.addColorStop(0.45, hexToRgba(u < 0.5 ? b : a, 0.35 * strength))
    grad.addColorStop(1, hexToRgba(color, 0))
    g.fillStyle = grad
    g.fillRect(x, 0, 2, 256)
  }
  const tex = new CanvasTexture(c)
  tex.colorSpace = SRGBColorSpace
  return tex
}

/** Eisfläche: Verlauf, Frostrisse, Glanzschlieren, Ridge-Ring, Schneeflocke. */
function drawGlacierFloor(
  g: CanvasRenderingContext2D,
  s: number,
  arena: ArenaDefinition,
  variant: ArenaVariant,
) {
  const p = variant.palette
  const R = arena.radius
  const rng = mulberry32(1234 + variant.id.length)
  radialFloor(g, s, R + 0.1, p.floor)

  // helle Glanzschlieren quer über das Eis
  for (let i = 0; i < 14; i++) {
    const a = rng() * Math.PI
    const off = (rng() - 0.5) * R * 1.6
    g.save()
    g.rotate(a)
    const grad = g.createLinearGradient(0, (off - 0.4) * s, 0, (off + 0.4) * s)
    grad.addColorStop(0, 'rgba(255,255,255,0)')
    grad.addColorStop(0.5, `rgba(255,255,255,${0.03 + rng() * 0.05})`)
    grad.addColorStop(1, 'rgba(255,255,255,0)')
    g.fillStyle = grad
    g.fillRect(-R * s, (off - 0.4) * s, 2 * R * s, 0.8 * s)
    g.restore()
  }

  // Frostrisse: verzweigte Zufallslinien
  g.lineCap = 'round'
  const crack = (x: number, y: number, a: number, len: number, width: number, depth: number) => {
    let cx = x
    let cy = y
    g.beginPath()
    g.moveTo(cx * s, cy * s)
    const steps = 6 + Math.floor(rng() * 6)
    for (let i = 0; i < steps; i++) {
      a += (rng() - 0.5) * 0.9
      cx += (Math.cos(a) * len) / steps
      cy += (Math.sin(a) * len) / steps
      g.lineTo(cx * s, cy * s)
      if (depth > 0 && rng() < 0.25)
        crack(cx, cy, a + (rng() - 0.5) * 2, len * 0.45, width * 0.6, depth - 1)
    }
    g.lineWidth = width * s
    g.strokeStyle = hexToRgba(p.line, variant.id === 'aurora' ? 0.35 : 0.55)
    g.stroke()
  }
  for (let i = 0; i < 26; i++) {
    const r = 1 + rng() * (R - 1.2)
    const a = rng() * Math.PI * 2
    crack(
      r * Math.cos(a),
      r * Math.sin(a),
      rng() * Math.PI * 2,
      0.6 + rng() * 1.2,
      0.008 + rng() * 0.01,
      2,
    )
  }

  // Ridge-Ring
  g.beginPath()
  g.arc(0, 0, arena.ridge.r * s, 0, Math.PI * 2)
  g.lineWidth = 0.025 * s
  g.strokeStyle = hexToRgba(p.accent, 0.45)
  g.stroke()

  // Randabschattung
  const shade = g.createRadialGradient(0, 0, (R - 1.4) * s, 0, 0, R * s)
  shade.addColorStop(0, 'rgba(0,0,0,0)')
  shade.addColorStop(1, variant.id === 'aurora' ? 'rgba(0,0,0,0.5)' : 'rgba(20,40,70,0.35)')
  g.fillStyle = shade
  g.beginPath()
  g.arc(0, 0, R * s, 0, Math.PI * 2)
  g.fill()

  // Schneeflocke
  g.strokeStyle = hexToRgba(p.accent, 0.75)
  g.lineWidth = 0.035 * s
  for (let i = 0; i < 6; i++) {
    g.save()
    g.rotate((i * Math.PI) / 3)
    g.beginPath()
    g.moveTo(0.18 * s, 0)
    g.lineTo(1.05 * s, 0)
    for (const at of [0.5, 0.78]) {
      g.moveTo(at * s, 0)
      g.lineTo((at + 0.16) * s, 0.14 * s)
      g.moveTo(at * s, 0)
      g.lineTo((at + 0.16) * s, -0.14 * s)
    }
    g.stroke()
    g.restore()
  }
  g.beginPath()
  g.arc(0, 0, 1.2 * s, 0, Math.PI * 2)
  g.lineWidth = 0.015 * s
  g.strokeStyle = hexToRgba(p.trim, 0.5)
  g.stroke()
}
