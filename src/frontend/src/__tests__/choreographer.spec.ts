import { describe, expect, it } from 'vitest'
import type { BattleReport, Side } from '@/api'
import {
  createChoreography,
  createPartPoses,
  createPose,
  type Choreography,
} from '@/game/choreographer'
import {
  arenaById,
  bowlY,
  CONTACT_DIST,
  pocketAt,
  pocketFloorY,
  STANDARD_ARENA,
  wrapAngle,
} from '@/game/arenas'
import fixtures from './fixtures/battles.json'
import kraterFixtures from './fixtures/battles-krater.json'

const battles = fixtures as unknown as Record<string, BattleReport>
const krater = kraterFixtures as unknown as Record<string, BattleReport>
const all = [...Object.values(battles), ...Object.values(krater)]

function poses(c: Choreography, side: Side, step = 50) {
  const out = []
  const p = createPose()
  for (let t = 0; t <= c.endMs; t += step) {
    c.sample(side, t, p)
    out.push({ ...p })
  }
  return out
}

describe('Choreograf', () => {
  it('liefert bei gleichem Seed identische Bahnen', () => {
    for (const report of all) {
      const a = createChoreography(report)
      const b = createChoreography(structuredClone(report))
      expect(poses(a, 'A')).toEqual(poses(b, 'A'))
      expect(poses(a, 'B')).toEqual(poses(b, 'B'))
    }
  })

  it('sieht mit anderem Seed anders aus', () => {
    const report = battles.DECISION!
    const a = poses(createChoreography(report), 'A', 500)
    const b = poses(createChoreography({ ...report, seed: (report.seed ?? 0) + 1 }), 'A', 500)
    expect(a).not.toEqual(b)
  })

  it('lässt die Kreisel genau zum Event-Zeitpunkt am Treffpunkt kollidieren', () => {
    const pa = createPose()
    const pb = createPose()
    for (const report of all) {
      const c = createChoreography(report)
      const clashes = report.timeline!.events.filter((e) => e.type === 'CLASH')
      expect(c.clashes).toHaveLength(clashes.length)
      for (const e of clashes) {
        c.sample('A', e.t, pa)
        c.sample('B', e.t, pb)
        const dist = Math.hypot(pa.x - pb.x, pa.z - pb.z)
        expect(Math.abs(dist - CONTACT_DIST)).toBeLessThan(0.05)
        const mid = Math.atan2((pa.z + pb.z) / 2, (pa.x + pb.x) / 2)
        expect(Math.abs(wrapAngle(mid - e.angle))).toBeLessThan(0.1)
      }
    }
  })

  it('hält beide Kreisel bis zum Finish in der Bowl', () => {
    for (const report of all) {
      const c = createChoreography(report)
      const arena = arenaById(report.arena?.id)
      for (const side of ['A', 'B'] as const) {
        const p = createPose()
        // nach dem Launch-Hüpfer liegt der Kreisel auf der Bowl
        for (let t = 500; t <= c.finishMs; t += 25) {
          c.sample(side, t, p)
          const r = Math.hypot(p.x, p.z)
          expect(r).toBeLessThan(arena.radius - 0.6)
          expect(Math.abs(p.y - bowlY(arena, r))).toBeLessThan(1e-3)
        }
      }
    }
  })

  it.each([
    ['Standard-Stadion', battles.RING_OUT!],
    ['Vulkan-Krater', krater.RING_OUT!],
  ])('schleudert beim Ring-Out in die Tasche aus der Timeline (%s)', (_, report) => {
    const arena = arenaById(report.arena?.id)
    const c = createChoreography(report)
    const ring = report.timeline!.events.find((e) => e.type === 'RING_OUT')
    const pocket = ring?.type === 'RING_OUT' ? ring.pocket : -2
    const p = c.sample(c.loser, c.endMs, createPose())
    const r = Math.hypot(p.x, p.z)
    expect(r).toBeGreaterThan(arena.radius)
    expect(r).toBeLessThan(arena.radius + arena.pocketDepth)
    expect(pocketAt(arena, Math.atan2(p.z, p.x))).toBe(pocket)
    expect(p.y).toBeCloseTo(pocketFloorY(arena) + 0.02, 1)
    // Der Sieger bleibt in der Bowl
    const w = c.sample(c.winner, c.endMs, createPose())
    expect(Math.hypot(w.x, w.z)).toBeLessThan(arena.radius)
  })

  it('lässt beim Burst die Teile auseinanderfliegen und auf der Bowl landen', () => {
    const report = battles.BURST!
    const c = createChoreography(report)
    expect(c.burst).not.toBeNull()
    const parts = createPartPoses()
    expect(c.sampleParts(c.finishMs - 10, parts)).toBe(false)
    expect(c.sampleParts(c.finishMs + 400, parts)).toBe(true)
    const spread = parts.map((p) => Math.hypot(p.x, p.z))
    expect(Math.max(...spread)).toBeGreaterThan(0.3)

    c.sampleParts(c.endMs, parts)
    for (const p of parts) {
      const x = c.burst!.x + p.x
      const z = c.burst!.z + p.z
      const r = Math.hypot(x, z)
      expect(r).toBeLessThan(STANDARD_ARENA.radius)
      const y = c.burst!.y + p.y
      expect(y).toBeGreaterThanOrEqual(bowlY(STANDARD_ARENA, r) - 0.6)
    }
    expect(c.sample(c.loser, c.finishMs + 100, createPose()).burst).toBe(true)
    expect(c.sample(c.winner, c.finishMs + 100, createPose()).burst).toBe(false)
  })

  it('lässt bei Spin-Out den Verlierer umkippen, der Sieger dreht weiter', () => {
    const report = battles.SPIN_OUT!
    const c = createChoreography(report)
    const l1 = c.sample(c.loser, c.finishMs + 2500, createPose())
    const l2 = c.sample(c.loser, c.endMs, createPose())
    expect(Math.hypot(l2.tiltX, l2.tiltZ)).toBeGreaterThan(0.9)
    expect(Math.abs(l2.spin - l1.spin)).toBeLessThan(0.5)
    const w1 = c.sample(c.winner, c.finishMs + 2500, createPose())
    const w2 = c.sample(c.winner, c.endMs, createPose())
    expect(Math.abs(w2.spin - w1.spin)).toBeGreaterThan(1)
    expect(Math.hypot(w2.tiltX, w2.tiltZ)).toBeLessThan(0.6)
  })
})
