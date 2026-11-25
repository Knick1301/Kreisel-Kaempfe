import {
  ULT_LABEL,
  type BattleReport,
  type BattleTimeline,
  type FighterDto,
  type MatchupDto,
  type Side,
  type TimelineEvent,
  type UltType,
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

/** So lange steht der rote Rest eines Treffers in der Spin-Leiste, dann läuft er nach. */
export const HIT_GHOST_HOLD_MS = 350
export const HIT_GHOST_FADE_MS = 450

/**
 * Spin vor den letzten Treffern, für den roten Rest in der Spin-Leiste: Treffer sollen sichtbar
 * Spin kosten, die Reibung nur ruhig. Liefert null, wenn gerade nichts nachläuft.
 */
export function hitGhost(timeline: BattleTimeline, side: Side, tMs: number) {
  const now = energyAt(timeline, side, tMs)
  let ghost = now
  const window = HIT_GHOST_HOLD_MS + HIT_GHOST_FADE_MS
  for (const e of timeline.events) {
    if (e.t > tMs) break
    if (e.type !== 'CLASH' || tMs - e.t >= window) continue
    // Spin kurz vor dem Treffer, danach mindestens bis zum nächsten Sample
    const before = energyAt(timeline, side, e.t - timeline.sampleMs)
    const age = tMs - e.t
    const k = age < HIT_GHOST_HOLD_MS ? 1 : 1 - (age - HIT_GHOST_HOLD_MS) / HIT_GHOST_FADE_MS
    ghost = Math.max(ghost, now + (before - now) * k)
  }
  return ghost - now > 0.004 ? ghost : null
}

/** Ult-Leiste (0–1) zum Zeitpunkt t, oder null bei Kämpfen ohne Ults. */
export function ultAt(timeline: BattleTimeline, side: Side, tMs: number) {
  const samples = timeline.ult?.[side]
  if (!samples || samples.length === 0) return null
  const i = Math.max(0, Math.floor(tMs / timeline.sampleMs))
  // Treppenstufe statt Interpolation: Nach dem Auslösen fällt die Leiste sofort auf 0
  return samples[Math.min(i, samples.length - 1)]!
}

/** Ausholen des Vernichtungsschlags bis zum Treffer, wie `SMASH_WINDUP_MS` im Backend. */
export const SMASH_WINDUP_MS = 500

export interface UltWindow {
  side: Side
  ult: UltType
  /** Auslösen */
  start: number
  /** Ende der Wirkung (beim Schlag: der Treffer) */
  end: number
  power: number
}

/** Zeitfenster aller Ults für Aura, Kamera und HUD. */
export function ultWindows(events: readonly TimelineEvent[]): UltWindow[] {
  const windows: UltWindow[] = []
  for (const e of events) {
    if (e.type === 'ULT') {
      const end = e.ult === 'SMASH' ? e.t + SMASH_WINDUP_MS : e.t + (e.durationMs ?? 0)
      windows.push({ side: e.side, ult: e.ult, start: e.t, end, power: e.intensity })
    } else if (e.type === 'ULT_END') {
      // vorzeitig beendet, z. B. Schild vom Vernichtungsschlag gebrochen
      const open = [...windows].reverse().find((w) => w.side === e.side && w.ult === e.ult)
      if (open && e.t < open.end) open.end = e.t
    }
  }
  return windows
}

/** Laufende Ult einer Seite zum Zeitpunkt t. */
export function activeUlt(windows: readonly UltWindow[], side: Side, tMs: number) {
  return windows.find((w) => w.side === side && tMs >= w.start && tMs < w.end) ?? null
}

/** Stärke als Wort, abhängig vom Anspinnen. */
export function ultPowerLabel(power: number) {
  if (power >= 1.15) return 'Maximal'
  if (power >= 1.0) return 'Stark'
  return null
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
  /** Ult-Einblendung: größer und in der Farbe der Ult */
  ult?: UltType
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
    case 'ULT': {
      const power = ultPowerLabel(e.intensity)
      return {
        text: `${ULT_LABEL[e.ult]}!${power ? ` · ${power}` : ''}`,
        tone: tone(e.side === me),
        ult: e.ult,
      }
    }
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
      if (e.ult === 'SMASH') return `${names[e.attacker]} schlägt mit voller Wucht zu (${pct} %)!`
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
    case 'ULT':
      return `${names[e.side]} entfesselt ${ULT_LABEL[e.ult]}!`
    case 'ULT_END':
      return e.ult === 'SHIELD' ? `Der Schildwall von ${names[e.side]} fällt.` : null
    case 'ULT_READY':
      return `Die Ult von ${names[e.side]} ist bereit!`
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
