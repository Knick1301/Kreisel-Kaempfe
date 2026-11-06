import type {
  BattleReport,
  BattleTimeline,
  FighterDto,
  MatchupDto,
  Side,
  TimelineEvent,
} from '@/api'

/** Reine Helfer für Wiedergabe, HUD und Textverlauf (ohne Vue/three). */

export const other = (side: Side): Side => (side === 'A' ? 'B' : 'A')

export const clamp = (v: number, min: number, max: number) => Math.max(min, Math.min(max, v))

export function smoothstep(x: number) {
  const t = clamp(x, 0, 1)
  return t * t * (3 - 2 * t)
}

/** Spin-Energie (0–1) zum Zeitpunkt t, linear zwischen den Samples interpoliert. */
export function energyAt(timeline: BattleTimeline, side: Side, tMs: number) {
  const samples = timeline.energy[side]
  if (samples.length === 0) return 0
  const f = Math.max(0, tMs) / timeline.sampleMs
  const i = Math.floor(f)
  if (i >= samples.length - 1) return samples[samples.length - 1]!
  const frac = f - i
  return samples[i]! * (1 - frac) + samples[i + 1]! * frac
}

/**
 * Energie für die Anzeige: nach dem Finish fällt der Verlierer auf 0, bei einer Entscheidung
 * dreht der Sieger noch.
 */
export function displayEnergy(report: BattleReport, side: Side, tMs: number) {
  const tl = report.timeline
  if (!tl) return 0
  const e = energyAt(tl, side, Math.min(tMs, report.durationMs ?? tMs))
  const finishMs = report.durationMs ?? Infinity
  if (tMs <= finishMs) return e
  const after = (tMs - finishMs) / 1000
  if (side === report.winnerSide) return e * Math.exp(-after * 0.08)
  const fade = report.finish === 'DECISION' ? 1.6 : 0.35
  return e * Math.max(0, 1 - after / fade)
}

export function formatClock(ms: number) {
  const s = Math.max(0, Math.floor(ms / 1000))
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`
}

/** Liegt direkt nach dem CLASH (gleicher Zeitpunkt) ein DEFLECT? */
export function isDeflected(events: readonly TimelineEvent[], index: number) {
  const e = events[index]
  for (let i = index + 1; i < events.length && events[i]!.t === e?.t; i++) {
    if (events[i]!.type === 'DEFLECT') return true
  }
  return false
}

export type Tone = 'good' | 'bad' | 'neutral'

export interface Callout {
  text: string
  tone: Tone
}

/** Ab dieser Intensität heißt ein Treffer „Volltreffer!“. */
export const BIG_HIT = 0.55

/** Kurze Einblendung zu einem Ereignis aus Sicht von `me`, oder null. */
export function calloutFor(
  events: readonly TimelineEvent[],
  index: number,
  me: Side,
): Callout | null {
  const e = events[index]!
  const tone = (good: boolean): Tone => (good ? 'good' : 'bad')
  switch (e.type) {
    case 'CLASH':
      if (isDeflected(events, index) || e.intensity < BIG_HIT) return null
      return { text: 'Volltreffer!', tone: tone(e.attacker === me) }
    case 'DEFLECT':
      return { text: 'Abgewehrt!', tone: tone(e.defender === me) }
    case 'NEAR_RING_OUT':
      return { text: 'Knapp!', tone: tone(e.victim !== me) }
    case 'BURST':
      return { text: 'Burst!', tone: tone(e.victim !== me) }
    case 'RING_OUT':
      return { text: 'Ring-Out!', tone: tone(e.victim !== me) }
    case 'SPIN_OUT':
      return { text: 'Ausgedreht!', tone: tone(e.victim !== me) }
    case 'TIMEOUT':
      return { text: 'Zeit!', tone: 'neutral' }
    default:
      return null
  }
}

/** Ein Satz pro Ereignis für den Textverlauf (ohne WebGL / reduzierte Bewegung). */
export function describeEvent(
  events: readonly TimelineEvent[],
  index: number,
  names: Record<Side, string>,
): string | null {
  const e = events[index]!
  switch (e.type) {
    case 'LAUNCH':
      return 'Let it rip! Beide Kreisel sind im Stadion.'
    case 'TORNADO':
      return 'Beide kreisen auf der Tornado-Ridge.'
    case 'ENGAGE':
      return 'Die Kreisel gehen aufeinander los.'
    case 'CLASH': {
      const pct = Math.round(e.intensity * 100)
      if (isDeflected(events, index)) return `${names[e.attacker]} greift an (${pct} %) …`
      return e.intensity >= BIG_HIT
        ? `Volltreffer von ${names[e.attacker]} (${pct} %)!`
        : `${names[e.attacker]} trifft (${pct} %).`
    }
    case 'DEFLECT':
      return `… ${names[e.defender]} wehrt ab, der Angreifer prallt zurück.`
    case 'NEAR_RING_OUT':
      return `${names[e.victim]} schrammt knapp an der Tasche vorbei!`
    case 'WOBBLE':
      return `${names[e.victim]} beginnt zu taumeln.`
    case 'BURST':
      return `Burst! ${names[e.victim]} fliegt in seine Teile.`
    case 'RING_OUT':
      return `Ring-Out! ${names[e.victim]} landet in der Tasche.`
    case 'SPIN_OUT':
      return `${names[e.victim]} hat ausgedreht.`
    case 'TIMEOUT':
      return 'Zeit! Die Restenergie entscheidet.'
  }
}

export function mySideOf(report: BattleReport, username: string): Side {
  const f = report.fighters
  if (f?.[1].username === username && f[0].username !== username) return 'B'
  return 'A'
}

export function fighterOf(report: BattleReport, side: Side): FighterDto | undefined {
  return report.fighters?.find((f) => f.side === side)
}

/** Matchup-Hinweis aus Sicht von `me`. */
export function matchupLabel(matchup: MatchupDto | undefined, me: Side) {
  if (!matchup?.favored) return 'Ausgeglichen'
  return matchup.favored === me ? 'Vorteil: Du' : 'Vorteil: Gegner'
}

/** Wiedergabegeschwindigkeit: Slow-Mo kurz um sehr harte Treffer und nach dem Finish. */
export function playbackRate(
  slowMo: readonly { t: number }[],
  finishMs: number,
  tMs: number,
  calm: boolean,
) {
  if (calm) return 1
  if (tMs >= finishMs - 80 && tMs < finishMs + 1500) return 0.32
  for (const s of slowMo) {
    if (tMs >= s.t - 140 && tMs < s.t + 320) return 0.42
  }
  return 1
}
