import { describe, expect, it } from 'vitest'
import { leagueChange, leagueOf } from '../game/league'

describe('Ligagrenzen (müssen zu RankingService passen)', () => {
  it('benennt Ligen und Divisionen', () => {
    expect(leagueOf(0)).toBe('Bronze III')
    expect(leagueOf(950)).toBe('Bronze III')
    expect(leagueOf(1000)).toBe('Bronze II')
    expect(leagueOf(1099)).toBe('Bronze I')
    expect(leagueOf(1100)).toBe('Silber III')
    expect(leagueOf(1399)).toBe('Gold I')
    expect(leagueOf(1700)).toBe('Meister')
  })

  it('erkennt Auf- und Abstieg', () => {
    expect(leagueChange(1095, 1101)).toBe(1)
    expect(leagueChange(1101, 1095)).toBe(-1)
    expect(leagueChange(1001, 1040)).toBe(0)
    expect(leagueChange(1690, 1710)).toBe(1)
  })
})
