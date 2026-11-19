import { describe, expect, it } from 'vitest'
import { plateHit, spinLoop } from '@/game/metalSynth'

const RATE = 8000

describe('Metallklänge', () => {
  it('Schlag aufs Blech: laut am Anfang, klingt aus, bleibt im Pegel', () => {
    const o = {
      low: 85,
      high: 3500,
      modes: 30,
      decay: 0.5,
      crack: 1,
      drive: 0.7,
      length: 1,
      seed: 1,
    }
    const d = plateHit(RATE, o)
    expect(d.length).toBe(RATE)
    const rms = (a: number, b: number) =>
      Math.sqrt(d.slice(a, b).reduce((s, x) => s + x * x, 0) / (b - a))
    expect(rms(0, 800)).toBeGreaterThan(rms(6000, 8000) * 4)
    expect(Math.max(...d.map(Math.abs))).toBeLessThanOrEqual(1)
    // gleicher Seed, gleicher Klang; anderer Seed, andere Variante
    expect(plateHit(RATE, o)).toEqual(d)
    expect(plateHit(RATE, { ...o, seed: 2 })).not.toEqual(d)
  })

  it('Surren ist eine nahtlose Schleife aus ganzen Umdrehungen', () => {
    const d = spinLoop(RATE, 1, 40)
    expect(d.length).toBe(RATE)
    // kein Sprung am Übergang Ende → Anfang
    const jump = Math.abs(d[0]! - d[d.length - 1]!)
    let typical = 0
    for (let i = 1; i < d.length; i++) typical += Math.abs(d[i]! - d[i - 1]!)
    typical /= d.length - 1
    expect(jump).toBeLessThan(typical * 6)
  })
})
