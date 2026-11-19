import type { ClashMark } from './choreographer'

/** Art eines Treffers für die Anzeige an der Trefferstelle. */
export type HitKind = 'smash' | 'critical' | 'hit' | 'graze' | 'blocked'

/** Ab dieser Wucht ist ein Treffer kritisch (sehr hart, oft mit Burst- oder Ring-Out-Gefahr). */
export const CRITICAL_HIT = 0.72
/** Darunter streift ein Treffer nur. */
export const GRAZE = 0.28

export const HIT_LABEL: Record<HitKind, string> = {
  smash: 'Vernichtend!',
  critical: 'Kritisch!',
  hit: 'Treffer',
  graze: 'Streifer',
  blocked: 'Abgewehrt!',
}

export function hitKind(c: Pick<ClashMark, 'deflected' | 'intensity' | 'ult'>): HitKind {
  if (c.ult === 'SMASH') return 'smash'
  if (c.deflected) return 'blocked'
  if (c.intensity >= CRITICAL_HIT) return 'critical'
  if (c.intensity < GRAZE) return 'graze'
  return 'hit'
}
