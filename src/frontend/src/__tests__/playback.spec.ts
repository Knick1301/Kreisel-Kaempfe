import { describe, expect, it } from 'vitest'
import type { BattleReport } from '@/api'
import {
  calloutFor,
  describeEvent,
  displayEnergy,
  energyAt,
  formatClock,
  matchupLabel,
  playbackRate,
} from '@/game/playback'
import { ARENAS, pocketAt, rimSegments, STANDARD_ARENA } from '@/game/arenas'
import fixtures from './fixtures/battles.json'

const battles = fixtures as unknown as Record<string, BattleReport>

describe('Wiedergabe', () => {
  it('interpoliert die Energie zwischen den Samples', () => {
    const tl = {
      sampleMs: 100,
      energy: { A: [1, 0.5, 0], B: [0, 0, 0] },
      hits: { A: 0, B: 0 },
      events: [],
    }
    expect(energyAt(tl, 'A', 0)).toBe(1)
    expect(energyAt(tl, 'A', 50)).toBeCloseTo(0.75)
    expect(energyAt(tl, 'A', 150)).toBeCloseTo(0.25)
    expect(energyAt(tl, 'A', 999)).toBe(0)
  })

  it('lässt den Verlierer nach dem Finish auf 0 fallen', () => {
    const r = battles.RING_OUT!
    const loser = r.winnerSide === 'A' ? 'B' : 'A'
    expect(displayEnergy(r, loser, r.durationMs! + 1000)).toBe(0)
    expect(displayEnergy(r, r.winnerSide!, r.durationMs! + 1000)).toBeGreaterThan(0)
  })

  it('formatiert die Kampfzeit', () => {
    expect(formatClock(0)).toBe('0:00')
    expect(formatClock(23_450)).toBe('0:23')
    expect(formatClock(65_000)).toBe('1:05')
  })

  it('blendet Treffer und Finish aus Spielersicht ein', () => {
    const r = battles.BURST!
    const events = r.timeline!.events
    const burst = events.findIndex((e) => e.type === 'BURST')
    const victim = (events[burst] as { victim: 'A' | 'B' }).victim
    const me = victim === 'A' ? 'B' : 'A'
    expect(calloutFor(events, burst, me)).toEqual({ text: 'Burst!', tone: 'good' })
    expect(calloutFor(events, burst, victim)?.tone).toBe('bad')
    expect(calloutFor(events, 0, 'A')).toBeNull()
  })

  it('beschreibt jedes Ereignis für den Textverlauf', () => {
    for (const r of Object.values(battles)) {
      const names = { A: 'Du', B: r.fighters![1].beyblade }
      r.timeline!.events.forEach((_, i) => {
        expect(describeEvent(r.timeline!.events, i, names)).toBeTruthy()
      })
    }
  })

  it('zeigt den Matchup-Hinweis aus Spielersicht', () => {
    expect(matchupLabel({ edge: 0.2, favored: 'A' }, 'A')).toBe('Vorteil: Du')
    expect(matchupLabel({ edge: 0.2, favored: 'A' }, 'B')).toBe('Vorteil: Gegner')
    expect(matchupLabel({ edge: 0.01, favored: null }, 'A')).toBe('Ausgeglichen')
  })

  it('verlangsamt um harte Treffer und am Ende, ruhige Version nie', () => {
    expect(playbackRate([{ t: 5000 }], 20_000, 5000, false)).toBeLessThan(1)
    expect(playbackRate([{ t: 5000 }], 20_000, 8000, false)).toBe(1)
    expect(playbackRate([], 20_000, 20_200, false)).toBeLessThan(1)
    expect(playbackRate([{ t: 5000 }], 20_000, 5000, true)).toBe(1)
  })
})

describe('Arenen', () => {
  it('Taschen passen zum Backend (Arena.java)', () => {
    expect(Object.keys(ARENAS)).toEqual(['standard'])
    expect(STANDARD_ARENA.pockets).toEqual([Math.PI / 2, (7 * Math.PI) / 6, (11 * Math.PI) / 6])
    expect(STANDARD_ARENA.pocketHalfWidth).toBeCloseTo((14 * Math.PI) / 180, 12)
  })

  it('findet Taschen und lässt dazwischen den Rand stehen', () => {
    expect(pocketAt(STANDARD_ARENA, Math.PI / 2 + 0.1)).toBe(0)
    expect(pocketAt(STANDARD_ARENA, 0)).toBe(-1)
    const segments = rimSegments(STANDARD_ARENA)
    expect(segments).toHaveLength(3)
    for (const [a0, a1] of segments) {
      expect(a1).toBeGreaterThan(a0)
      expect(pocketAt(STANDARD_ARENA, (a0 + a1) / 2)).toBe(-1)
    }
  })
})
