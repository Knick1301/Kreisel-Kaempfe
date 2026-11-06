import { describe, expect, it } from 'vitest'
import type { BattleReport, LaunchChallenge } from '@/api'
import {
  frameToWorld,
  launchDetail,
  launchVerdict,
  MISSED,
  needleAt,
  previewLaunch,
  zoneAt,
  zoneScore,
} from '@/game/launch'
import { createStandbyChoreography } from '@/game/standby'
import {
  createChoreography,
  createPose,
  LAUNCH_FLY_S,
  launchRestRadius,
} from '@/game/choreographer'
import { arenaById } from '@/game/arenas'
import fixtures from './fixtures/battles.json'

const c: LaunchChallenge = {
  windowMs: 3000,
  maxTaps: 3,
  periodMs: 1400,
  zones: [0.2, 0.5, 0.8],
  zoneHalf: 0.05,
  coreHalf: 0.014,
  edgeScore: 0.5,
  perfectQuality: 0.97,
}

/** Erster Zeitpunkt, zu dem der Zeiger auf Stand `p` ist (Hinweg). */
const timeAt = (p: number) => Math.round((c.periodMs / (2 * Math.PI)) * Math.acos(1 - 2 * p))

describe('Anspinnen mit dem Schwungmesser', () => {
  it('Zeiger pendelt: an den Enden langsam, in der Mitte schnell', () => {
    expect(needleAt(c, 0)).toBeCloseTo(0)
    expect(needleAt(c, 700)).toBeCloseTo(1)
    expect(needleAt(c, 1400)).toBeCloseTo(0)
    const end = needleAt(c, 50) - needleAt(c, 0)
    const mid = needleAt(c, 375) - needleAt(c, 325)
    expect(mid).toBeGreaterThan(5 * end)
  })

  it('Kern gibt volle Punkte, Rand die Hälfte, daneben nichts (wie der Server)', () => {
    expect(zoneScore(c, 0)).toBe(1)
    expect(zoneScore(c, 0.014)).toBe(1)
    expect(zoneScore(c, 0.05)).toBeCloseTo(0.5)
    expect(zoneScore(c, 0.051)).toBe(0)
    expect(zoneAt(c, 0.5)).toEqual({ zone: 1, score: 1 })
    expect(zoneAt(c, 0.35)).toBeNull()
  })

  it('alle drei Kerne sind perfekt', () => {
    const l = previewLaunch(c, { taps: [timeAt(0.2), timeAt(0.5), timeAt(0.8)] })
    expect(l).toMatchObject({ hits: 3, cores: 3, quality: 1, perfect: true, missed: false })
    expect(launchVerdict(l)).toBe('Perfekt!')
    expect(launchDetail(l)).toBe('3/3 Zonen · 3× Kern · 100 %')
  })

  it('feine Abstufungen nach Zonen und Kern', () => {
    const two = previewLaunch(c, { taps: [timeAt(0.2), timeAt(0.5), timeAt(0.65)] })
    expect(two.hits).toBe(2)
    expect(launchVerdict(two)).toBe('Stark!')
    const edges = previewLaunch(c, { taps: [timeAt(0.245), timeAt(0.545), timeAt(0.845)] })
    expect(edges).toMatchObject({ hits: 3, cores: 0 })
    expect(edges.quality).toBeGreaterThan(0.45)
    expect(edges.quality).toBeLessThan(0.7)
    expect(launchVerdict(edges)).toBe('Volltreffer!')
    // wer besser trifft, fliegt weiter in die Bowl
    expect(two.landingX!).toBeGreaterThan(edges.landingX!)
  })

  it('dieselbe Zone zählt nur einmal, daneben ist verschenkt', () => {
    const t = timeAt(0.5)
    expect(previewLaunch(c, { taps: [t, 1400 - t, 1400 + t] }).hits).toBe(1)
    const miss = previewLaunch(c, { taps: [timeAt(0.35), timeAt(0.65)] })
    expect(miss.missed).toBe(true)
    expect(launchVerdict(miss)).toBe('Daneben!')
  })

  it('unplausible Eingaben zählen als verpasst', () => {
    expect(previewLaunch(c, { taps: [] })).toEqual(MISSED)
    expect(previewLaunch(c, { taps: [500, 300] })).toEqual(MISSED)
    expect(previewLaunch(c, { taps: [3001] })).toEqual(MISSED)
    expect(previewLaunch(c, { taps: [1, 2, 3, 4] })).toEqual(MISSED)
  })

  it('Wartestellung liegt am Startpunkt, der Kreisel fliegt zu seinem Landepunkt', () => {
    const report = (fixtures as unknown as Record<string, BattleReport>).SPIN_OUT!
    const arena = arenaById(report.arena?.id)
    const launch = (landingX: number) => ({
      quality: 0.8,
      hits: 3,
      cores: 1,
      landingX,
      landingY: 0,
      perfect: false,
      missed: false,
    })
    const launched: BattleReport = {
      ...report,
      fighters: [
        { ...report.fighters![0], launch: launch(-0.5) },
        { ...report.fighters![1], launch: launch(-0.7) },
      ],
    }
    const real = createChoreography(launched)
    const standby = createStandbyChoreography(report.arena?.id)
    for (const side of ['A', 'B'] as const) {
      const a = standby.sample(side, -1, createPose())
      const b = real.sample(side, 0, createPose())
      expect(a.x).toBeCloseTo(b.x, 3)
      expect(a.y).toBeCloseTo(b.y, 3)
      expect(a.z).toBeCloseTo(b.z, 3)
    }
    const land = frameToWorld('A', { x: -0.5, y: 0 }, launchRestRadius(arena))
    const after = real.sample('A', LAUNCH_FLY_S * 1000 + 10, createPose())
    expect(Math.hypot(after.x - land.x, after.z - land.z)).toBeLessThan(0.15)
    expect(real.landings?.length).toBe(2)
    expect(standby.sample('A', -1, createPose()).x).toBeLessThan(0)
    expect(standby.sample('B', -1, createPose()).x).toBeGreaterThan(0)
  })
})
