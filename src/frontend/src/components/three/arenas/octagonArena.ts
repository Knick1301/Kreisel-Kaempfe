import {
  BoxGeometry,
  BufferGeometry,
  CylinderGeometry,
  ConeGeometry,
  Float32BufferAttribute,
  PlaneGeometry,
} from 'three'
import { RoundedBoxGeometry } from 'three/examples/jsm/geometries/RoundedBoxGeometry.js'
import { bowlY, pocketFloorY, type ArenaDefinition, type ArenaVariant } from '@/game/arenas'
import {
  ArenaKit,
  drawLogoInto,
  drawTexture,
  faceUp,
  hexToRgba,
  makeHaloTexture,
  place,
  planarUV,
  radialFloor,
  wallFrame,
  type BuiltArena,
} from './kit'

/**
 * Stahl-Oktagon: achteckiger Käfig mit flachem Boden. Chromwände mit eingelassenen, leuchtenden
 * Kunststoff-Paneelen, goldene Abschlussleisten und Nieten, achteckige Eckpfeiler mit Kristallspitze, vier
 * Lücken mit Lichtpfosten und leuchtender Auffangrinne. Der Boden trägt ein Wabenraster, Warnwinkel vor den
 * Ausgängen und das Emblem in der Mitte.
 */

const SIDES = 8
const WALL_H = 0.72
const WALL_T = 0.22
const WALL_GAP = 0.12

export function buildOctagonArena(arena: ArenaDefinition, variant: ArenaVariant): BuiltArena {
  const p = variant.palette
  const kit = new ArenaKit(arena, p)
  const R = arena.radius
  const base = bowlY(arena, R)
  const wallMid = R + WALL_GAP + WALL_T / 2
  const halfEdge = (R + WALL_GAP) * Math.tan(Math.PI / SIDES)
  const pocketHalf = R * Math.tan(arena.pocketHalfWidth)
  const neon = variant.id === 'neon'

  // Neon: schwarzer Chrom, der die Studio-Umgebung nur schwach spiegelt
  const chrome = kit.chrome(p.metal, neon ? 0.3 : 0.14, neon ? 0.45 : 1.25)
  const dark = kit.chrome(neon ? '#121118' : '#2b2e35', 0.35, neon ? 0.35 : 1)
  const gold = kit.trim()
  const panel = kit.plastic(p.accent, neon ? 0.9 : 0.4, neon ? 0.7 : 0.78)
  const strip = kit.glow(p.accent2)
  const exitGlow = kit.glow(p.accent)
  const pocketMat = kit.glow(p.pocketFloor, 0.85)
  const crystal = kit.crystal(p.accent, neon ? 1.1 : 0.6)

  // ---------------------------------------------------------------- Boden
  const floorSize = 2 * (R + 0.4)
  const floorTex = kit.own(drawTexture(floorSize, (g, s) => drawOctagonFloor(g, s, arena, variant)))
  drawLogoInto(floorTex, floorSize, 2.1, neon ? 0.55 : 0.4)
  kit.mesh(
    kit.floor(floorTex, neon ? 0.3 : 0.7),
    planarUV(octagonFloor(arena, R + 0.02), floorSize),
  )

  // ---------------------------------------------------------------- Wände
  for (let k = 0; k < SIDES; k++) {
    const theta = (k * Math.PI * 2) / SIDES
    const f = wallFrame(theta)
    const hasGap = arena.pockets.some(
      (q) => Math.abs(Math.atan2(Math.sin(q - theta), Math.cos(q - theta))) < 1e-6,
    )
    const segments: [number, number][] = hasGap
      ? [
          [-halfEdge, -pocketHalf - 0.02],
          [pocketHalf + 0.02, halfEdge],
        ]
      : [[-halfEdge, halfEdge]]

    for (const [s0, s1] of segments) {
      const len = s1 - s0
      const mid = (s0 + s1) / 2
      const c = f.at(wallMid, mid)
      kit.add(
        chrome,
        place(
          new RoundedBoxGeometry(len, WALL_H, WALL_T, 3, 0.05),
          c.x,
          base + WALL_H / 2,
          c.z,
          0,
          f.rotY,
        ),
      )

      // eingelassenes, leuchtendes Paneel auf der Innenseite
      if (len > 0.5) {
        const pi = f.at(wallMid - WALL_T / 2 - 0.005, mid)
        kit.add(
          panel,
          place(
            new RoundedBoxGeometry(len - 0.34, WALL_H - 0.3, 0.05, 2, 0.02),
            pi.x,
            base + WALL_H / 2 + 0.02,
            pi.z,
            0,
            f.rotY,
          ),
        )
        // Nieten an den Ecken des Paneels
        for (const [da, dy] of [
          [-1, -1],
          [1, -1],
          [-1, 1],
          [1, 1],
        ] as const) {
          const rv = f.at(wallMid - WALL_T / 2 - 0.01, mid + da * (len / 2 - 0.1))
          kit.add(
            gold,
            place(
              new CylinderGeometry(0.04, 0.04, 0.03, 12),
              rv.x,
              base + WALL_H / 2 + dy * (WALL_H / 2 - 0.1),
              rv.z,
              Math.PI / 2,
              f.rotY,
              0,
              'YXZ',
            ),
          )
        }
      }
      // goldene Abschlussleiste oben
      const top = f.at(wallMid, mid)
      kit.add(
        gold,
        place(
          new RoundedBoxGeometry(len + 0.02, 0.07, WALL_T + 0.08, 2, 0.025),
          top.x,
          base + WALL_H + 0.02,
          top.z,
          0,
          f.rotY,
        ),
      )
      // Lichtleiste am Wandfuß
      const lb = f.at(R + WALL_GAP - 0.01, mid)
      kit.add(
        strip,
        place(new BoxGeometry(len - 0.08, 0.035, 0.025), lb.x, base + 0.05, lb.z, 0, f.rotY),
      )
    }

    // Abzeichen in der Mitte geschlossener Wände
    if (!hasGap) {
      const b = f.at(wallMid - WALL_T / 2 - 0.035, 0)
      kit.add(
        gold,
        place(
          new CylinderGeometry(0.17, 0.17, 0.04, 8),
          b.x,
          base + WALL_H / 2 + 0.02,
          b.z,
          Math.PI / 2,
          f.rotY,
          0,
          'YXZ',
        ),
      )
      kit.add(
        crystal,
        place(
          new CylinderGeometry(0.1, 0.12, 0.05, 8),
          b.x,
          base + WALL_H / 2 + 0.02,
          b.z,
          Math.PI / 2,
          f.rotY,
          0,
          'YXZ',
        ),
      )
    } else {
      buildExit(kit, arena, f, pocketHalf, base, { chrome, dark, exitGlow, pocketMat })
    }
  }

  // ---------------------------------------------------------------- Eckpfeiler
  const pillarR = wallMid / Math.cos(Math.PI / SIDES)
  for (let k = 0; k < SIDES; k++) {
    const a = ((k + 0.5) * Math.PI * 2) / SIDES
    const x = Math.cos(a) * pillarR
    const z = Math.sin(a) * pillarR
    const h = WALL_H + 0.28
    kit.add(
      chrome,
      place(new CylinderGeometry(0.25, 0.3, h, 8), x, base + h / 2, z, 0, Math.PI / 8),
    )
    kit.add(gold, place(new CylinderGeometry(0.32, 0.32, 0.07, 8), x, base + h, z, 0, Math.PI / 8))
    kit.add(
      gold,
      place(new CylinderGeometry(0.33, 0.33, 0.06, 8), x, base + 0.05, z, 0, Math.PI / 8),
    )
    kit.add(crystal, place(new ConeGeometry(0.13, 0.34, 6), x, base + h + 0.2, z))
    kit.add(crystal, place(new CylinderGeometry(0.13, 0.15, 0.06, 6), x, base + h + 0.05, z))
  }

  // ---------------------------------------------------------------- Sockel
  const plinthApothem = R + 1.35
  const plinthR = plinthApothem / Math.cos(Math.PI / SIDES)
  kit.add(
    dark,
    place(
      octagonRing(
        R + WALL_GAP + WALL_T - 0.02,
        plinthApothem,
        base + 0.001,
        arena.pockets,
        pocketHalf,
        R + arena.pocketDepth + 0.12,
      ),
      0,
      0,
      0,
    ),
  )
  kit.add(
    dark,
    place(
      new CylinderGeometry(plinthR, plinthR * 1.05, 1.4, SIDES, 1, true),
      0,
      base - 0.7,
      0,
      0,
      Math.PI / 8,
    ),
  )
  kit.add(
    gold,
    place(
      new CylinderGeometry(plinthR + 0.01, plinthR + 0.01, 0.06, SIDES, 1, true),
      0,
      base - 0.02,
      0,
      0,
      Math.PI / 8,
    ),
  )

  // Lichthöfe an den Ausgängen
  const halo = kit.own(makeHaloTexture())
  const haloMat = kit.halo(halo, p.accent, neon ? 0.85 : 0.45)
  for (const q of arena.pockets) {
    const hx = Math.cos(q) * (R + 0.55)
    const hz = Math.sin(q) * (R + 0.55)
    kit.mesh(haloMat, place(new PlaneGeometry(2.4, 2.4), hx, base + 0.02, hz, -Math.PI / 2), 2)
    kit.light(p.accent, neon ? 7 : 3.5, hx, base + 0.5, hz, 4)
  }

  return kit.finish()
}

/** Ausgang in einer Wand: Lichtpfosten, Stufe, Auffangrinne mit Seiten- und Rückwand. */
function buildExit(
  kit: ArenaKit,
  arena: ArenaDefinition,
  f: ReturnType<typeof wallFrame>,
  pocketHalf: number,
  base: number,
  m: {
    chrome: ReturnType<ArenaKit['chrome']>
    dark: ReturnType<ArenaKit['chrome']>
    exitGlow: ReturnType<ArenaKit['glow']>
    pocketMat: ReturnType<ArenaKit['glow']>
  },
) {
  const R = arena.radius
  const floorY = pocketFloorY(arena)
  const back = R + arena.pocketDepth
  const width = 2 * pocketHalf + 0.06
  // Lichtpfosten links und rechts der Lücke
  for (const side of [-1, 1]) {
    const post = f.at(R + WALL_GAP + 0.02, side * (pocketHalf + 0.04))
    kit.add(
      m.exitGlow,
      place(
        new BoxGeometry(0.05, WALL_H - 0.06, 0.05),
        post.x,
        base + WALL_H / 2,
        post.z,
        0,
        f.rotY,
      ),
    )
  }
  // Stufe vom Boden hinunter in die Rinne
  const step = f.at(R + 0.01, 0)
  kit.add(
    m.chrome,
    place(
      new BoxGeometry(width, base - floorY, 0.02),
      step.x,
      (base + floorY) / 2,
      step.z,
      0,
      f.rotY,
    ),
  )
  // Boden der Rinne (leuchtet)
  const fl = f.at((R + back) / 2, 0)
  kit.add(m.pocketMat, place(new BoxGeometry(width, 0.02, back - R), fl.x, floorY, fl.z, 0, f.rotY))
  // Seitenwände und Rückwand
  const wallTop = base + 0.35
  for (const side of [-1, 1]) {
    const sw = f.at((R + back) / 2, side * (pocketHalf + 0.06))
    kit.add(
      m.dark,
      place(
        new BoxGeometry(0.06, wallTop - floorY, back - R),
        sw.x,
        (wallTop + floorY) / 2,
        sw.z,
        0,
        f.rotY,
      ),
    )
  }
  const bw = f.at(back + 0.03, 0)
  kit.add(
    m.dark,
    place(
      new BoxGeometry(width + 0.12, wallTop - floorY, 0.06),
      bw.x,
      (wallTop + floorY) / 2,
      bw.z,
      0,
      f.rotY,
    ),
  )
}

/** Achteckiger Boden, Kanten auf den Wänden (Kantenmitten bei θ = k·45°), Höhe aus dem Bowl-Profil. */
function octagonFloor(arena: ArenaDefinition, apothem: number, rings = 40, perEdge = 18) {
  const corner = apothem / Math.cos(Math.PI / SIDES)
  const outline: [number, number][] = []
  for (let k = 0; k < SIDES; k++) {
    const a0 = ((k - 0.5) * Math.PI * 2) / SIDES
    const a1 = ((k + 0.5) * Math.PI * 2) / SIDES
    for (let i = 0; i < perEdge; i++) {
      const u = i / perEdge
      outline.push([
        corner * (Math.cos(a0) + (Math.cos(a1) - Math.cos(a0)) * u),
        corner * (Math.sin(a0) + (Math.sin(a1) - Math.sin(a0)) * u),
      ])
    }
  }
  const n = outline.length
  const pos: number[] = []
  const idx: number[] = []
  for (let j = 0; j <= rings; j++) {
    const s = j / rings
    for (const [ox, oz] of outline) {
      const x = ox * s
      const z = oz * s
      pos.push(x, bowlY(arena, Math.min(Math.hypot(x, z), arena.radius)), z)
    }
  }
  for (let j = 0; j < rings; j++) {
    for (let i = 0; i < n; i++) {
      const a = j * n + i
      const b = j * n + ((i + 1) % n)
      const c = (j + 1) * n + i
      const d = (j + 1) * n + ((i + 1) % n)
      // Wicklung so, dass die Normalen nach oben zeigen
      idx.push(a, b, c, b, d, c)
    }
  }
  const g = new BufferGeometry()
  g.setAttribute('position', new Float32BufferAttribute(pos, 3))
  g.setIndex(idx)
  g.computeVertexNormals()
  return g
}

/** Achteckiger flacher Kranz zwischen zwei Apothemen, mit Aussparungen für die Rinnen. */
function octagonRing(
  inner: number,
  outer: number,
  y: number,
  pockets: readonly number[],
  pocketHalf: number,
  pocketBack: number,
) {
  const parts: number[] = []
  const quad = (
    ax: number,
    az: number,
    bx: number,
    bz: number,
    cx: number,
    cz: number,
    dx: number,
    dz: number,
  ) => {
    parts.push(ax, y, az, cx, y, cz, bx, y, bz, bx, y, bz, cx, y, cz, dx, y, dz)
  }
  const ci = inner / Math.cos(Math.PI / SIDES)
  const co = outer / Math.cos(Math.PI / SIDES)
  for (let k = 0; k < SIDES; k++) {
    const theta = (k * Math.PI * 2) / SIDES
    const a0 = theta - Math.PI / SIDES
    const a1 = theta + Math.PI / SIDES
    const gap = pockets.some(
      (q) => Math.abs(Math.atan2(Math.sin(q - theta), Math.cos(q - theta))) < 1e-6,
    )
    const p = (r: number, a: number) => [r * Math.cos(a), r * Math.sin(a)] as const
    if (!gap) {
      const [ax, az] = p(ci, a0)
      const [bx, bz] = p(ci, a1)
      const [cx, cz] = p(co, a0)
      const [dx, dz] = p(co, a1)
      quad(ax, az, bx, bz, cx, cz, dx, dz)
      continue
    }
    // Kante in drei Stücke: links, Rinne (nur hinter der Rinne), rechts
    const f = wallFrame(theta)
    const edge = (out: number, along: number) => f.at(out, along)
    const half = inner * Math.tan(Math.PI / SIDES)
    const halfO = outer * Math.tan(Math.PI / SIDES)
    const w = pocketHalf + 0.09
    for (const [s0, s1] of [
      [-1, -w],
      [w, 1],
    ] as const) {
      const ia = edge(inner, s0 === -1 ? -half : s0)
      const ib = edge(inner, s1 === 1 ? half : s1)
      const oa = edge(outer, s0 === -1 ? -halfO : s0)
      const ob = edge(outer, s1 === 1 ? halfO : s1)
      quad(ia.x, ia.z, ib.x, ib.z, oa.x, oa.z, ob.x, ob.z)
    }
    const ra = edge(pocketBack, -w)
    const rb = edge(pocketBack, w)
    const sa = edge(outer, -w)
    const sb = edge(outer, w)
    quad(ra.x, ra.z, rb.x, rb.z, sa.x, sa.z, sb.x, sb.z)
  }
  const g = new BufferGeometry()
  g.setAttribute('position', new Float32BufferAttribute(parts, 3))
  return faceUp(g)
}

/** Boden: Verlauf, Wabenraster, achteckige Ringe, Warnwinkel vor den Ausgängen, Emblem. */
function drawOctagonFloor(
  g: CanvasRenderingContext2D,
  s: number,
  arena: ArenaDefinition,
  variant: ArenaVariant,
) {
  const p = variant.palette
  const R = arena.radius
  radialFloor(g, s, R + 0.4, p.floor)

  // Wabenraster
  const hex = 0.32
  g.lineWidth = 0.012 * s
  g.strokeStyle = hexToRgba(p.line, variant.id === 'neon' ? 0.45 : 0.28)
  const w = Math.sqrt(3) * hex
  for (let row = -20; row <= 20; row++) {
    for (let col = -20; col <= 20; col++) {
      const cx = col * w + (row % 2 ? w / 2 : 0)
      const cy = row * hex * 1.5
      if (Math.hypot(cx, cy) > R + 0.3) continue
      g.beginPath()
      for (let i = 0; i < 6; i++) {
        const a = Math.PI / 6 + (i * Math.PI) / 3
        const x = (cx + hex * Math.cos(a)) * s
        const y = (cy + hex * Math.sin(a)) * s
        if (i === 0) g.moveTo(x, y)
        else g.lineTo(x, y)
      }
      g.closePath()
      g.stroke()
    }
  }

  const octagon = (apothem: number) => {
    const c = apothem / Math.cos(Math.PI / SIDES)
    g.beginPath()
    for (let k = 0; k <= SIDES; k++) {
      const a = ((k + 0.5) * Math.PI * 2) / SIDES
      const x = c * Math.cos(a) * s
      const y = c * Math.sin(a) * s
      if (k === 0) g.moveTo(x, y)
      else g.lineTo(x, y)
    }
  }
  // achteckige Führungslinien
  for (const [ap, alpha, width] of [
    [1.25, 0.8, 0.02],
    [2.5, 0.55, 0.016],
    [3.55, 0.9, 0.03],
  ] as const) {
    octagon(ap)
    g.strokeStyle = hexToRgba(p.accent, alpha * (variant.id === 'neon' ? 0.9 : 0.55))
    g.lineWidth = width * s
    g.stroke()
  }
  // Randabschattung zur Wand hin
  octagon(R + 0.02)
  const edge = g.createRadialGradient(0, 0, (R - 1.2) * s, 0, 0, (R + 0.4) * s)
  edge.addColorStop(0, 'rgba(0,0,0,0)')
  edge.addColorStop(1, 'rgba(0,0,0,0.55)')
  g.fillStyle = edge
  g.fill()

  // Warnwinkel vor den Ausgängen
  for (const q of arena.pockets) {
    g.save()
    g.rotate(q)
    for (let i = 0; i < 3; i++) {
      const x = (R - 0.95 + i * 0.28) * s
      g.beginPath()
      g.moveTo(x, -0.32 * s)
      g.lineTo(x + 0.18 * s, 0)
      g.lineTo(x, 0.32 * s)
      g.lineWidth = 0.06 * s
      g.strokeStyle = hexToRgba(p.accent, 0.35 + i * 0.2)
      g.stroke()
    }
    g.restore()
  }

  // Emblem in der Mitte: Achteck-Ring mit Stern
  octagon(0.62)
  g.lineWidth = 0.05 * s
  g.strokeStyle = hexToRgba(p.trim, 0.85)
  g.stroke()
  octagon(0.5)
  g.lineWidth = 0.012 * s
  g.strokeStyle = hexToRgba(p.accent, 0.9)
  g.stroke()
  g.beginPath()
  for (let i = 0; i < 16; i++) {
    const r = (i % 2 ? 0.12 : 0.36) * s
    const a = (i * Math.PI) / 8
    if (i === 0) g.moveTo(r * Math.cos(a), r * Math.sin(a))
    else g.lineTo(r * Math.cos(a), r * Math.sin(a))
  }
  g.closePath()
  g.fillStyle = hexToRgba(p.accent, 0.35)
  g.fill()
}
