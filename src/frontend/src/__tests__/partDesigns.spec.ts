import { describe, expect, it } from 'vitest'
import type { PartDto } from '@/api'
import {
  botDesigns,
  designsFor,
  modelKey,
  TIP_DESIGNS,
  TRACK_DESIGNS,
  WHEEL_DESIGNS,
} from '@/game/partDesigns'

const part = (modelPath: string, extra: Partial<PartDto> = {}): PartDto => ({
  id: 1,
  name: 'Teil',
  modelPath,
  weight: 30,
  attack: 0,
  defense: 0,
  stamina: 0,
  dash: 0,
  burstResistance: 0,
  ...extra,
})

describe('Teile-Designs', () => {
  it('liest den Modellnamen aus dem Pfad', () => {
    expect(modelKey('fusion-wheels/drachenzahn.glb')).toBe('drachenzahn')
    expect(modelKey(null)).toBe('')
  })

  it('hat für jedes Teil im Katalog ein eigenes Design', () => {
    // muss zu src/backend/src/main/resources/parts/*.json passen
    expect(Object.keys(WHEEL_DESIGNS)).toHaveLength(6)
    expect(Object.keys(TRACK_DESIGNS)).toHaveLength(6)
    expect(Object.keys(TIP_DESIGNS)).toHaveLength(6)
    const shapes = new Set(Object.values(WHEEL_DESIGNS).map((d) => d.shape))
    expect(shapes.size).toBe(6)
  })

  it('findet Designs über den Modellpfad und fällt sonst auf die Werte zurück', () => {
    const d = designsFor([
      part('fusion-wheels/eisenmond.glb'),
      part('spin-tracks/feder.glb'),
      part('tips/kralle.glb'),
    ])
    expect(d.wheel.shape).toBe('moon')
    expect(d.track.style).toBe('spring')
    expect(d.tip.style).toBe('claw')

    const fallback = designsFor([
      part('fusion-wheels/neu.glb', { attack: 7 }),
      part('spin-tracks/neu.glb', { stamina: 8, defense: 2 }),
      part('tips/neu.glb', { dash: 2, stamina: 3 }),
    ])
    expect(fallback.wheel.shape).toBe('fang')
    expect(fallback.track.style).toBe('orbit')
    expect(fallback.tip.style).toBe('needle')
  })

  it('gibt KI-Gegnern stabile Designs in ihrer Akzentfarbe', () => {
    const stats = { attack: 6, defense: 4, stamina: 9, dash: 7, weight: 76, burstResistance: 2 }
    const a = botDesigns(stats, '#8fb4ff', 42)
    expect(botDesigns(stats, '#8fb4ff', 42)).toEqual(a)
    expect(a.wheel.accent).toBe('#8fb4ff')
    expect(a.track.accent).toBe('#8fb4ff')
  })
})
