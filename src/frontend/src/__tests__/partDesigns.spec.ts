import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import type { PartDto } from '@/api'
import {
  botDesigns,
  botLook,
  botModelPaths,
  designsFor,
  modelKey,
  PART_STATS,
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
    expect(Object.keys(WHEEL_DESIGNS)).toHaveLength(7)
    expect(Object.keys(TRACK_DESIGNS)).toHaveLength(7)
    expect(Object.keys(TIP_DESIGNS)).toHaveLength(7)
    const shapes = new Set(Object.values(WHEEL_DESIGNS).map((d) => d.shape))
    expect(shapes.size).toBe(7)
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

describe('Aussehen der KI-Gegner', () => {
  const readCatalog = (file: string) =>
    JSON.parse(
      readFileSync(
        resolve(__dirname, '../../../backend/src/main/resources/parts', `${file}.json`),
        'utf8',
      ),
    ) as { modelPath: string; attack?: number; defense?: number; stamina?: number; dash?: number }[]

  it('Katalogwerte stimmen mit dem Backend überein', () => {
    const table = (file: string, fields: readonly string[]) =>
      Object.fromEntries(
        readCatalog(file).map((p) => [
          modelKey(p.modelPath),
          Object.fromEntries(fields.map((f) => [f, p[f as keyof typeof p]])),
        ]),
      )
    expect(PART_STATS.wheels).toEqual(table('fusion-wheels', ['attack', 'defense']))
    expect(PART_STATS.tracks).toEqual(table('spin-tracks', ['defense', 'stamina']))
    expect(PART_STATS.tips).toEqual(table('tips', ['stamina', 'dash']))
  })

  it('nimmt nie die Teile des Gegners und bleibt über den Seed stabil', () => {
    // Spieler mit Phantom, Turm, Kugel; KI mit fast gleichen Werten
    const avoid = ['phantom', 'turm', 'kugel']
    const stats = { attack: 5, defense: 6, stamina: 12, dash: 5, weight: 78, burstResistance: 2 }
    for (let seed = 0; seed < 200; seed++) {
      const look = botLook(stats, seed, avoid)
      expect(avoid).not.toContain(look.wheel)
      expect(avoid).not.toContain(look.track)
      expect(avoid).not.toContain(look.tip)
      expect(botLook(stats, seed, avoid)).toEqual(look)
    }
    // Abwechslung zwischen verschiedenen Gegnern
    const wheels = new Set(Array.from({ length: 60 }, (_, s) => botLook(stats, s, avoid).wheel))
    expect(wheels.size).toBeGreaterThanOrEqual(3)
  })

  it('sucht Teile passend zu den Werten', () => {
    const attacker = { attack: 7, defense: 3, stamina: 8, dash: 8, weight: 75, burstResistance: 2 }
    const defender = { attack: 1, defense: 12, stamina: 8, dash: 2, weight: 86, burstResistance: 4 }
    for (let seed = 0; seed < 50; seed++) {
      expect(['hells-scythe-blade', 'drachenzahn', 'greif']).toContain(
        botLook(attacker, seed).wheel,
      )
      expect(['bollwerk', 'eisenmond', 'wirbel']).toContain(botLook(defender, seed).wheel)
      expect(['hells-scythe-bit', 'kralle', 'speer']).toContain(botLook(attacker, seed).tip)
    }
    const paths = botModelPaths(attacker, 1)
    expect(paths[0]).toMatch(/^fusion-wheels\/.+\.glb$/)
    expect(paths[1]).toMatch(/^spin-tracks\/.+\.glb$/)
    expect(paths[2]).toMatch(/^tips\/.+\.glb$/)
  })
})
