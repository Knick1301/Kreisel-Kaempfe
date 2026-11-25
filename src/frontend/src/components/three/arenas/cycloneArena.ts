import {
  BufferGeometry,
  CylinderGeometry,
  ExtrudeGeometry,
  Float32BufferAttribute,
  PlaneGeometry,
  Shape,
  TorusGeometry,
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
 * Zyklon-Kessel: tiefer, steiler Kessel mit Kuppel in der Mitte, darauf ein Nabenemblem wie auf den
 * Kreisel-Chips. Spiralarme im Boden, eine hohe Chromwand und darauf ein Kranz aus geschwungenen,
 * leuchtenden Wirbelfinnen. Sechs kleine Taschen mit leuchtendem Boden.
 */

const FINS_PER_SEGMENT = 2

export function buildCycloneArena(arena: ArenaDefinition, variant: ArenaVariant): BuiltArena {
  const p = variant.palette
  const kit = new ArenaKit(arena, p)
  const R = arena.radius
  const edge = bowlY(arena, R)
  const rim = rimY(arena)
  const wallTop = rim + 0.3
  const sand = variant.id === 'sand'

  const chrome = kit.chrome(p.metal, sand ? 0.26 : 0.14)
  const dark = kit.chrome(sand ? '#3b2a17' : '#141a24', 0.4)
  const gold = kit.trim()
  const fin = kit.plastic(p.accent, sand ? 0.45 : 0.75, 0.88, 0.35)
  const finCore = kit.plastic(p.accent2, sand ? 0.35 : 0.6, 0.92, 0.35)
  const glowLine = kit.glow(p.accent)
  const pocketMat = kit.glow(p.pocketFloor, 0.9)

  // ---------------------------------------------------------------- Boden mit Spiralarmen und Nabe
  const size = 2 * (R + 0.1)
  const tex = kit.own(drawTexture(size, (g, s) => drawCycloneFloor(g, s, arena, variant)))
  drawLogoInto(tex, size, 1.3, 0.55)
  kit.mesh(kit.floor(tex, sand ? 0.25 : 0.5), kit.bowlSurface(R, size, 120, 240))

  // Goldring am Fuß der Kuppel, Nabenkappe in der Mitte
  if (arena.dome) {
    const dr = arena.dome.r
    kit.add(
      gold,
      place(new TorusGeometry(dr, 0.028, 10, 128), 0, bowlY(arena, dr) + 0.012, 0, Math.PI / 2),
    )
    kit.add(
      gold,
      place(new TorusGeometry(0.62, 0.02, 8, 64), 0, bowlY(arena, 0.62) + 0.01, 0, Math.PI / 2),
    )
  }

  // ---------------------------------------------------------------- Kesselwand zwischen den Taschen
  for (const [a0, a1] of rimSegments(arena)) {
    const seg = Math.max(8, Math.ceil(((a1 - a0) * 180) / Math.PI))
    kit.add(
      chrome,
      revolve(
        [
          [R, edge],
          [R + 0.05, wallTop - 0.04],
          [R + 0.12, wallTop],
          [R + 0.55, wallTop + 0.04],
          [R + 0.66, wallTop - 0.08],
        ],
        a0,
        a1,
        seg,
      ),
    )
    kit.add(
      gold,
      revolve(
        [
          [R + 0.1, wallTop + 0.006],
          [R + 0.17, wallTop + 0.012],
        ],
        a0,
        a1,
        seg,
      ),
    )
    // Lichtlinie am Wandfuß
    kit.add(
      glowLine,
      revolve(
        [
          [R - 0.04, edge - 0.004],
          [R + 0.012, edge + 0.035],
        ],
        a0,
        a1,
        seg,
      ),
    )
    // Wirbelfinnen auf der Wand
    const span = a1 - a0
    const width = span / FINS_PER_SEGMENT
    for (let i = 0; i < FINS_PER_SEGMENT; i++) {
      const start = a0 + width * i + width * 0.08
      const arc = width * 0.84
      kit.add(fin, vortexFin(R + 0.36, wallTop + 0.02, start, arc, 0.72, 0.06))
      kit.add(
        finCore,
        vortexFin(R + 0.4, wallTop + 0.02, start + arc * 0.12, arc * 0.55, 0.38, 0.08),
      )
      // Halterung und Niete
      const mid = start + arc * 0.2
      kit.add(
        gold,
        place(
          new CylinderGeometry(0.05, 0.05, 0.05, 12),
          Math.cos(mid) * (R + 0.3),
          wallTop + 0.05,
          Math.sin(mid) * (R + 0.3),
        ),
      )
    }
  }

  // ---------------------------------------------------------------- Taschen
  for (const q of arena.pockets) {
    pocket(kit, arena, q, { chrome, dark, pocketMat, glowLine }, wallTop)
  }

  // ---------------------------------------------------------------- Außenschale
  kit.add(
    dark,
    place(new CylinderGeometry(R + 0.66, R + 0.95, 2.6, 96, 1, true), 0, wallTop - 0.08 - 1.3, 0),
  )
  kit.add(
    gold,
    place(new CylinderGeometry(R + 0.665, R + 0.68, 0.05, 96, 1, true), 0, wallTop - 0.11, 0),
  )
  kit.add(
    gold,
    place(new CylinderGeometry(R + 0.8, R + 0.82, 0.04, 96, 1, true), 0, wallTop - 1.0, 0),
  )

  // ---------------------------------------------------------------- Licht
  const halo = kit.own(makeHaloTexture())
  const haloMat = kit.halo(halo, p.accent, sand ? 0.35 : 0.6)
  kit.mesh(
    haloMat,
    place(new PlaneGeometry(2.8, 2.8), 0, bowlY(arena, 0) + 0.02, 0, -Math.PI / 2),
    2,
  )
  kit.light(p.accent2, sand ? 2.5 : 4, 0, 1.6, 0, 5)
  for (let i = 0; i < 3; i++) {
    const a = (i * 2 * Math.PI) / 3 + 0.4
    kit.light(
      p.accent,
      sand ? 3 : 5,
      Math.cos(a) * (R + 0.4),
      wallTop + 0.7,
      Math.sin(a) * (R + 0.4),
      5,
    )
  }
  for (const q of arena.pockets) {
    kit.light(
      p.pocketFloor,
      2.2,
      Math.cos(q) * (R + 0.45),
      pocketFloorY(arena) + 0.4,
      Math.sin(q) * (R + 0.45),
      2.5,
    )
  }

  return kit.finish()
}

/**
 * Geschwungene Finne: Umriss als Klinge, extrudiert und dann um die Bowl gebogen. x der Form läuft entlang
 * des Bogens, y nach oben, die Dicke radial. Die Spitze fächert sich nach außen auf.
 */
function vortexFin(
  radius: number,
  y0: number,
  start: number,
  arc: number,
  height: number,
  depth: number,
) {
  const L = radius * arc
  const s = new Shape()
  s.moveTo(0, 0)
  s.lineTo(L, 0)
  s.quadraticCurveTo(L * 1.06, height * 0.45, L * 0.92, height)
  s.quadraticCurveTo(L * 0.62, height * 0.62, L * 0.18, height * 0.22)
  s.quadraticCurveTo(0.02, height * 0.12, 0, 0)
  const g = new ExtrudeGeometry(s, {
    depth,
    bevelEnabled: true,
    bevelThickness: 0.012,
    bevelSize: 0.012,
    bevelSegments: 2,
    curveSegments: 16,
    steps: 1,
  })
  const pos = g.attributes.position!
  for (let i = 0; i < pos.count; i++) {
    const x = pos.getX(i)
    const y = pos.getY(i)
    const z = pos.getZ(i)
    const u = Math.max(0, Math.min(1, x / L))
    const h = Math.max(0, y) / height
    // nach außen aufgefächert, je höher, desto mehr: die Finnen rahmen die Bowl, ohne sie zu verdecken
    const r = radius + z + 0.38 * h * h - 0.06 * u
    const a = start + x / radius
    pos.setXYZ(i, Math.cos(a) * r, y0 + y, Math.sin(a) * r)
  }
  g.deleteAttribute('uv')
  g.computeVertexNormals()
  return g as BufferGeometry
}

function pocket(
  kit: ArenaKit,
  arena: ArenaDefinition,
  q: number,
  m: {
    chrome: ReturnType<ArenaKit['chrome']>
    dark: ReturnType<ArenaKit['chrome']>
    pocketMat: ReturnType<ArenaKit['glow']>
    glowLine: ReturnType<ArenaKit['glow']>
  },
  wallTop: number,
) {
  const R = arena.radius
  const a0 = q - arena.pocketHalfWidth
  const a1 = q + arena.pocketHalfWidth
  const floor = pocketFloorY(arena)
  const edge = bowlY(arena, R)
  const back = R + arena.pocketDepth
  const seg = 10
  kit.add(
    m.pocketMat,
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
    m.chrome,
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
    m.dark,
    revolve(
      [
        [back, floor],
        [back, wallTop],
      ],
      a0,
      a1,
      seg,
    ),
  )
  kit.add(
    m.glowLine,
    revolve(
      [
        [back - 0.02, wallTop + 0.003],
        [back + 0.04, wallTop + 0.003],
      ],
      a0,
      a1,
      seg,
    ),
  )
  for (const a of [a0, a1]) kit.add(m.dark, radialWall(a, R, back, floor, wallTop))
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

/** Boden: Verlauf, sechs Spiralarme, Windschlieren, Ridge-Ring und Nabe auf der Kuppel. */
function drawCycloneFloor(
  g: CanvasRenderingContext2D,
  s: number,
  arena: ArenaDefinition,
  variant: ArenaVariant,
) {
  const p = variant.palette
  const R = arena.radius
  const rng = mulberry32(arena.id.length * 977 + variant.id.length)
  radialFloor(g, s, R, p.floor)

  const r0 = (arena.dome?.r ?? 1) * 0.9
  const swirl = 1.35
  const spiral = (phase: number, r: number) => phase + swirl * Math.log(r / r0)

  // Spiralarme als sich verbreiternde Bänder
  for (let arm = 0; arm < 6; arm++) {
    const phase = (arm * Math.PI) / 3
    for (const [alpha, width] of [
      [0.16, 0.26],
      [0.55, 0.035],
    ] as const) {
      g.beginPath()
      const steps = 90
      const pts: [number, number][] = []
      for (let i = 0; i <= steps; i++) {
        const r = r0 + ((R - 0.05 - r0) * i) / steps
        const a = spiral(phase, r)
        const w = width * (0.3 + (0.7 * (r - r0)) / (R - r0))
        pts.push([(r * Math.cos(a) - w * Math.sin(a)) * s, (r * Math.sin(a) + w * Math.cos(a)) * s])
      }
      for (let i = steps; i >= 0; i--) {
        const r = r0 + ((R - 0.05 - r0) * i) / steps
        const a = spiral(phase, r)
        const w = width * (0.3 + (0.7 * (r - r0)) / (R - r0))
        pts.push([(r * Math.cos(a) + w * Math.sin(a)) * s, (r * Math.sin(a) - w * Math.cos(a)) * s])
      }
      pts.forEach(([x, y], i) => (i === 0 ? g.moveTo(x, y) : g.lineTo(x, y)))
      g.closePath()
      g.fillStyle = hexToRgba(alpha > 0.3 ? p.accent : p.line, alpha)
      g.fill()
    }
  }

  // feine Windschlieren
  g.lineCap = 'round'
  for (let i = 0; i < 90; i++) {
    const r = r0 + rng() * (R - r0 - 0.1)
    const a = rng() * Math.PI * 2
    const len = 0.25 + rng() * 0.6
    g.beginPath()
    g.arc(0, 0, r * s, a, a + len / r)
    g.lineWidth = (0.006 + rng() * 0.012) * s
    g.strokeStyle = hexToRgba(p.accent2, 0.08 + rng() * 0.14)
    g.stroke()
  }

  // Ridge-Ring, gestrichelt
  g.setLineDash([0.12 * s, 0.08 * s])
  g.beginPath()
  g.arc(0, 0, arena.ridge.r * s, 0, Math.PI * 2)
  g.lineWidth = 0.02 * s
  g.strokeStyle = hexToRgba(p.accent, 0.5)
  g.stroke()
  g.setLineDash([])

  // Randabschattung
  const shade = g.createRadialGradient(0, 0, (R - 1) * s, 0, 0, R * s)
  shade.addColorStop(0, 'rgba(0,0,0,0)')
  shade.addColorStop(1, 'rgba(0,0,0,0.5)')
  g.fillStyle = shade
  g.beginPath()
  g.arc(0, 0, R * s, 0, Math.PI * 2)
  g.fill()

  // Nabe auf der Kuppel: Achteck, Ringe, Schaufeln
  const hub = arena.dome?.r ?? 1.2
  const ring = (r: number, w: number, color: string) => {
    g.beginPath()
    g.arc(0, 0, r * s, 0, Math.PI * 2)
    g.lineWidth = w * s
    g.strokeStyle = color
    g.stroke()
  }
  const hubFill = g.createRadialGradient(0, 0, 0, 0, 0, hub * s)
  hubFill.addColorStop(0, hexToRgba(p.accent2, 0.35))
  hubFill.addColorStop(0.7, hexToRgba(p.accent, 0.12))
  hubFill.addColorStop(1, 'rgba(0,0,0,0)')
  g.fillStyle = hubFill
  g.beginPath()
  g.arc(0, 0, hub * s, 0, Math.PI * 2)
  g.fill()
  g.beginPath()
  for (let k = 0; k <= 8; k++) {
    const a = ((k + 0.5) * Math.PI) / 4
    const r = 0.85 / Math.cos(Math.PI / 8)
    if (k === 0) g.moveTo(r * Math.cos(a) * s, r * Math.sin(a) * s)
    else g.lineTo(r * Math.cos(a) * s, r * Math.sin(a) * s)
  }
  g.lineWidth = 0.035 * s
  g.strokeStyle = hexToRgba(p.trim, 0.8)
  g.stroke()
  ring(0.42, 0.02, hexToRgba(p.accent, 0.9))
  for (let i = 0; i < 6; i++) {
    const a = (i * Math.PI) / 3
    g.save()
    g.rotate(a)
    g.beginPath()
    g.moveTo(0.45 * s, 0)
    g.quadraticCurveTo(0.62 * s, 0.1 * s, 0.78 * s, 0.24 * s)
    g.quadraticCurveTo(0.6 * s, 0.02 * s, 0.45 * s, 0)
    g.fillStyle = hexToRgba(p.accent, 0.7)
    g.fill()
    g.restore()
  }
}
