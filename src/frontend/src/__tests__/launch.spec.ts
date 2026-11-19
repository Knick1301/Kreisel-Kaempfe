import { describe, expect, it } from 'vitest'
import type { BattleReport, LaunchChallenge } from '@/api'
import {
  frameToWorld,
  launchDetail,
  launchVerdict,
  MISSED,
  needleAt,
  previewLaunch,
  PULL_BASE,
  pullFactor,
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
  maxTaps: 1,
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

  it('im Kern losgelassen, voll und schnell gezogen ist perfekt', () => {
    const l = previewLaunch(c, { taps: [timeAt(0.5)], pull: 1, speed: 1 })
    expect(l).toMatchObject({ hits: 1, cores: 1, quality: 1, perfect: true, missed: false })
    expect(launchVerdict(l)).toBe('Perfekt!')
    expect(launchDetail(l)).toBe('Kern · 100 %')
  })

  it('kurzes Zucken gibt trotz gutem Timing nur einen schwachen Start', () => {
    const weak = previewLaunch(c, { taps: [timeAt(0.5)], pull: 0, speed: 0 })
    expect(weak.quality).toBeCloseTo(PULL_BASE)
    expect(launchVerdict(weak)).toBe('Wackelig')
    const strong = previewLaunch(c, { taps: [timeAt(0.2)], pull: 0.9, speed: 0.8 })
    expect(strong.quality).toBeCloseTo(pullFactor(0.9, 0.8))
    expect(launchVerdict(strong)).toBe('Hervorragend!')
    // wer besser startet, fliegt weiter in die Bowl
    expect(strong.landingX!).toBeGreaterThan(weak.landingX!)
  })

  it('Rand der Zone gibt halbes Timing, daneben ist verpasst', () => {
    const edge = previewLaunch(c, { taps: [timeAt(0.55)], pull: 1, speed: 1 })
    expect(edge).toMatchObject({ hits: 1, cores: 0 })
    expect(edge.quality).toBeCloseTo(0.5, 1)
    expect(launchDetail(edge)).toMatch(/^Zone · (49|50|51) %$/)
    const miss = previewLaunch(c, { taps: [timeAt(0.35)], pull: 1, speed: 1 })
    expect(miss.missed).toBe(true)
    expect(launchVerdict(miss)).toBe('Daneben!')
  })

  it('unplausible Eingaben zählen als verpasst', () => {
    expect(previewLaunch(c, { taps: [] })).toEqual(MISSED)
    expect(previewLaunch(c, { taps: [300, 500] })).toEqual(MISSED)
    expect(previewLaunch(c, { taps: [3001] })).toEqual(MISSED)
    expect(previewLaunch(c, { taps: [-1] })).toEqual(MISSED)
  })

  it('Wartestellung liegt am Startpunkt, der Kreisel fliegt zu seinem Landepunkt', () => {
    const report = (fixtures as unknown as Record<string, BattleReport>).SPIN_OUT!
    const arena = arenaById(report.arena?.id)
    const launch = (landingX: number) => ({
      quality: 0.8,
      hits: 1,
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
