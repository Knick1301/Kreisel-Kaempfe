/**
 * Ligagrenzen wie im Backend (RankingService.TIERS, DIVISION_WIDTH). Nur für Vergleiche im Frontend,
 * z. B. „Aufstieg“ nach einem Kampf. Der angezeigte Stand kommt immer vom Server.
 */
export const TIERS = [
  { name: 'Bronze', floor: 950 },
  { name: 'Silber', floor: 1100 },
  { name: 'Gold', floor: 1250 },
  { name: 'Platin', floor: 1400 },
  { name: 'Diamant', floor: 1550 },
  { name: 'Meister', floor: 1700 },
] as const

const DIVISION_WIDTH = 50
const ROMAN = ['', 'I', 'II', 'III']

/** Liga-Name zu einem Rating, z. B. "Silber II". */
export function leagueOf(rating: number): string {
  let index = 0
  TIERS.forEach((t, i) => {
    if (rating >= t.floor) index = i
  })
  const tier = TIERS[index]!
  if (index === TIERS.length - 1) return tier.name
  const step = Math.min(2, Math.max(0, Math.floor((rating - tier.floor) / DIVISION_WIDTH)))
  return `${tier.name} ${ROMAN[3 - step]}`
}

/** Positiv = Aufstieg, negativ = Abstieg, 0 = gleiche Division. */
export function leagueChange(before: number, after: number): number {
  const order = (r: number) => {
    let index = 0
    TIERS.forEach((t, i) => {
      if (r >= t.floor) index = i
    })
    if (index === TIERS.length - 1) return index * 3
    const step = Math.min(2, Math.max(0, Math.floor((r - TIERS[index]!.floor) / DIVISION_WIDTH)))
    return index * 3 + step
  }
  return Math.sign(order(after) - order(before))
}
