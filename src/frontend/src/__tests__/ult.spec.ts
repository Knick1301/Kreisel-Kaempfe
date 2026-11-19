import { describe, expect, it } from 'vitest'
import type { BattleTimeline, LaunchChallenge, TimelineEvent } from '@/api'
import { previewLaunch } from '@/game/launch'
import {
  activeUlt,
  calloutFor,
  describeEvent,
  HIT_GHOST_FADE_MS,
  HIT_GHOST_HOLD_MS,
  hitGhost,
  SMASH_WINDUP_MS,
  ultAt,
  ultWindows,
} from '@/game/playback'

const events: TimelineEvent[] = [
  { t: 0, type: 'LAUNCH' },
  { t: 5000, type: 'ULT', side: 'A', ult: 'SHIELD', intensity: 1.2, durationMs: 4000 },
  { t: 6000, type: 'ULT', side: 'B', ult: 'SMASH', intensity: 0.9 },
  { t: 6500, type: 'CLASH', attacker: 'B', intensity: 0.9, angle: 1, ult: 'SMASH' },
  { t: 6500, type: 'ULT_END', side: 'A', ult: 'SHIELD' },
]

describe('Ults in der Wiedergabe', () => {
  it('baut Zeitfenster, ein gebrochener Schild endet vorzeitig', () => {
    const w = ultWindows(events)
    expect(w).toHaveLength(2)
    expect(w[0]).toMatchObject({ side: 'A', ult: 'SHIELD', start: 5000, end: 6500 })
    expect(w[1]).toMatchObject({
      side: 'B',
      ult: 'SMASH',
      start: 6000,
      end: 6000 + SMASH_WINDUP_MS,
    })
    expect(activeUlt(w, 'A', 5500)?.ult).toBe('SHIELD')
    expect(activeUlt(w, 'A', 7000)).toBeNull()
    expect(activeUlt(w, 'B', 6200)?.ult).toBe('SMASH')
  })

  it('liest die Leiste als Treppe und fehlt bei Altbestand', () => {
    const tl: BattleTimeline = {
      sampleMs: 100,
      energy: { A: [1, 1, 1], B: [1, 1, 1] },
      hits: { A: 0, B: 0 },
      events: [],
      ult: { A: [0.2, 1, 0], B: [0, 0.5, 0.6] },
    }
    expect(ultAt(tl, 'A', 150)).toBe(1)
    expect(ultAt(tl, 'A', 250)).toBe(0)
    expect(ultAt(tl, 'B', 9999)).toBe(0.6)
    expect(ultAt({ ...tl, ult: undefined }, 'A', 100)).toBeNull()
  })

  it('kündigt Ults groß an, mit Stärke aus dem Anspinnen', () => {
    const c = calloutFor(events, 1, 'A')
    expect(c).toEqual({ text: 'Schildwall! · Maximal', tone: 'good', ult: 'SHIELD' })
    expect(calloutFor(events, 2, 'A')).toEqual({
      text: 'Vernichtungsschlag!',
      tone: 'bad',
      ult: 'SMASH',
    })
    const names = { A: 'Bollwerk', B: 'Sturm' }
    expect(describeEvent(events, 3, names)).toContain('voller Wucht')
    expect(describeEvent(events, 4, names)).toContain('Schildwall von Bollwerk fällt')
  })
})

describe('Ziehen beim Anspinnen', () => {
  const c: LaunchChallenge = {
    windowMs: 3000,
    maxTaps: 1,
    periodMs: 1300,
    zones: [0.2, 0.5, 0.8],
    zoneHalf: 0.05,
    coreHalf: 0.014,
    edgeScore: 0.5,
    perfectQuality: 0.97,
  }

  it('die Zugrichtung lenkt nur den Landepunkt', () => {
    const straight = previewLaunch(c, { taps: [325], pull: 1, speed: 1 })
    const right = previewLaunch(c, { taps: [325], pull: 1, speed: 1, aim: 1 })
    const wild = previewLaunch(c, { taps: [325], pull: 1, speed: 1, aim: -9 })
    expect(right.quality).toBe(straight.quality)
    expect(straight.landingY).toBe(0)
    expect(right.landingY).toBe(0.35)
    expect(wild.landingY).toBe(-0.35)
  })
})

describe('Treffer in der Spin-Leiste', () => {
  const tl: BattleTimeline = {
    sampleMs: 100,
    // B verliert bei t = 1000 durch einen Treffer 0,1, sonst kaum Reibung
    energy: { A: Array(30).fill(0.9), B: [...Array(10).fill(0.9), ...Array(20).fill(0.8)] },
    hits: { A: 1, B: 0 },
    events: [{ t: 1000, type: 'CLASH', attacker: 'A', intensity: 0.7, angle: 0 }],
  }

  it('zeigt den Spin vor dem Treffer kurz rot und lässt ihn nachlaufen', () => {
    expect(hitGhost(tl, 'B', 900)).toBeNull()
    expect(hitGhost(tl, 'B', 1200)).toBeCloseTo(0.9)
    const fading = hitGhost(tl, 'B', 1000 + HIT_GHOST_HOLD_MS + HIT_GHOST_FADE_MS / 2)!
    expect(fading).toBeGreaterThan(0.8)
    expect(fading).toBeLessThan(0.9)
    expect(hitGhost(tl, 'B', 1000 + HIT_GHOST_HOLD_MS + HIT_GHOST_FADE_MS + 10)).toBeNull()
    // der Angreifer hat nichts verloren
    expect(hitGhost(tl, 'A', 1200)).toBeNull()
  })
})
