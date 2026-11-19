import { BufferGeometry, Float32BufferAttribute } from 'three'
import { mergeGeometries } from 'three/examples/jsm/utils/BufferGeometryUtils.js'
import {
  bowlY,
  baseBowlY,
  pocketFloorY,
  rimSegments,
  rimY,
  type ArenaDefinition,
} from '@/game/arenas'

/**
 * Rotationsfläche aus einem Profil [r, y] über den Winkelbereich a0…a1
 * (Konvention wie im Choreografen: x = r·cos θ, z = r·sin θ). UV: u entlang des Winkels, v entlang des Profils.
 */
export function revolve(profile: [number, number][], a0: number, a1: number, segments: number) {
  const positions: number[] = []
  const uvs: number[] = []
  const index: number[] = []
  const rows = profile.length
  for (let j = 0; j <= segments; j++) {
    const a = a0 + ((a1 - a0) * j) / segments
    const c = Math.cos(a)
    const s = Math.sin(a)
    for (let i = 0; i < rows; i++) {
      const [r, y] = profile[i]!
      positions.push(r * c, y, r * s)
      uvs.push(j / segments, i / (rows - 1))
    }
  }
  for (let j = 0; j < segments; j++) {
    for (let i = 0; i < rows - 1; i++) {
      const p = j * rows + i
      const q = (j + 1) * rows + i
      index.push(p, p + 1, q, q, p + 1, q + 1)
    }
  }
  const g = new BufferGeometry()
  g.setAttribute('position', new Float32BufferAttribute(positions, 3))
  g.setAttribute('uv', new Float32BufferAttribute(uvs, 2))
  g.setIndex(index)
  g.computeVertexNormals()
  return g
}

/** Senkrechte Seitenwand einer Tasche bei Winkel a, von r0 bis r1 und y0 bis y1. */
function sideWall(a: number, r0: number, r1: number, y0: number, y1: number) {
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
  g.setAttribute('uv', new Float32BufferAttribute([0, 0, 1, 0, 0, 1, 1, 1], 2))
  g.setIndex([0, 1, 2, 2, 1, 3])
  g.computeVertexNormals()
  return g
}

export const LIP_WIDTH = 1.05

export interface ArenaGeometries {
  bowl: BufferGeometry
  /** Rand, Lippe, Taschen, Außenwand – alles in dunklem Metall, als eine Geometrie */
  metal: BufferGeometry
  pocketFloors: BufferGeometry
  /** Akzentlinien an Rand und Taschenkante */
  accents: BufferGeometry
  /** Feine Linie auf der Tornado-Ridge */
  ridgeLine: BufferGeometry
  dispose(): void
}

export function buildArenaGeometries(a: ArenaDefinition): ArenaGeometries {
  const R = a.radius
  const rim = rimY(a)
  const edge = baseBowlY(a, R)
  const floor = pocketFloorY(a)
  const outer = R + LIP_WIDTH
  const deg = (rad: number) => Math.max(4, Math.ceil((rad * 180) / Math.PI / 1.5))

  const profile: [number, number][] = Array.from({ length: 65 }, (_, i) => {
    const r = (i / 64) * R
    return [r, bowlY(a, r)]
  })
  const bowl = revolve(profile, 0, 2 * Math.PI, 192)

  const metalParts: BufferGeometry[] = []
  const accentParts: BufferGeometry[] = []
  for (const [a0, a1] of rimSegments(a)) {
    const seg = deg(a1 - a0)
    metalParts.push(
      revolve(
        [
          [R, edge],
          [R + 0.06, rim],
        ],
        a0,
        a1,
        seg,
      ),
      revolve(
        [
          [R + 0.06, rim],
          [outer, rim],
        ],
        a0,
        a1,
        seg,
      ),
    )
    accentParts.push(
      revolve(
        [
          [R + 0.05, rim + 0.002],
          [R + 0.09, rim + 0.002],
        ],
        a0,
        a1,
        seg,
      ),
    )
  }

  const floors: BufferGeometry[] = []
  for (const p of a.pockets) {
    const a0 = p - a.pocketHalfWidth
    const a1 = p + a.pocketHalfWidth
    const seg = deg(a1 - a0)
    const back = R + a.pocketDepth
    floors.push(
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
    metalParts.push(
      // Stufe von der Bowl-Kante runter in die Tasche
      revolve(
        [
          [R, floor],
          [R, edge],
        ],
        a0,
        a1,
        seg,
      ),
      // Rückwand und Lippe dahinter
      revolve(
        [
          [back, floor],
          [back, rim],
        ],
        a0,
        a1,
        seg,
      ),
      revolve(
        [
          [back, rim],
          [outer, rim],
        ],
        a0,
        a1,
        seg,
      ),
      sideWall(a0, R, back, floor, rim),
      sideWall(a1, R, back, floor, rim),
    )
    accentParts.push(
      revolve(
        [
          [back - 0.02, rim + 0.002],
          [back + 0.02, rim + 0.002],
        ],
        a0,
        a1,
        seg,
      ),
    )
  }
  metalParts.push(
    revolve(
      [
        [outer, rim],
        [outer, -1],
      ],
      0,
      2 * Math.PI,
      192,
    ),
  )

  const ridgeR = a.ridge.r
  const ridgeLine = revolve(
    [
      [ridgeR - 0.006, bowlY(a, ridgeR - 0.006) + 0.003],
      [ridgeR + 0.006, bowlY(a, ridgeR + 0.006) + 0.003],
    ],
    0,
    2 * Math.PI,
    240,
  )

  const metal = mergeGeometries(metalParts)!
  const pocketFloors = mergeGeometries(floors)!
  const accents = mergeGeometries(accentParts)!
  for (const g of [...metalParts, ...floors, ...accentParts]) g.dispose()

  return {
    bowl,
    metal,
    pocketFloors,
    accents,
    ridgeLine,
    dispose() {
      for (const g of [bowl, metal, pocketFloors, accents, ridgeLine]) g.dispose()
    },
  }
}
