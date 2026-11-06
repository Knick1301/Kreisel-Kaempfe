import type { LaunchChallenge, LaunchInput, LaunchResult, Side } from '@/api'

/**
 * Anspinnen mit dem Schwungmesser: Ein Zeiger pendelt über einen Bogen, an den Enden langsam, in der Mitte
 * schnell. Drei Zonen mit schmalem Kern, drei Sekunden, drei Taps, jede Zone zählt einmal. Der Client misst
 * nur die Zeitpunkte und zeigt eine Vorschau. Gewertet wird ausschließlich auf dem Server (gleiche Formeln wie
 * `LaunchChallenge.java`).
 */

export const SIGNAL = 'Spin!'

export interface Vec2 {
  x: number
  y: number
}

/** Stand des Zeigers auf dem Bogen (0 = links, 1 = rechts) zur Zeit `tMs` ab Start. */
export function needleAt(c: LaunchChallenge, tMs: number) {
  return 0.5 - 0.5 * Math.cos((2 * Math.PI * tMs) / c.periodMs)
}

/** Wert eines Taps mit Abstand `d` zur Zonenmitte: Kern 1, Rand `edgeScore`, außerhalb 0. */
export function zoneScore(c: LaunchChallenge, d: number) {
  if (d <= c.coreHalf) return 1
  if (d > c.zoneHalf) return 0
  return 1 - ((1 - c.edgeScore) * (d - c.coreHalf)) / (c.zoneHalf - c.coreHalf)
}

/** Zone unter dem Zeigerstand `p` mit Wert, oder null daneben. */
export function zoneAt(c: LaunchChallenge, p: number): { zone: number; score: number } | null {
  for (let i = 0; i < c.zones.length; i++) {
    const score = zoneScore(c, Math.abs(p - c.zones[i]!))
    if (score > 0) return { zone: i, score }
  }
  return null
}

const round3 = (v: number) => Math.round(v * 1000) / 1000

/** Flugweite in die Bowl wie auf dem Server (Abschussrahmen). */
const FLY_MIN = 0.25
const FLY_RANGE = 0.3

export const MISSED: LaunchResult = {
  quality: 0,
  hits: 0,
  cores: 0,
  landingX: -1 + FLY_MIN,
  landingY: 0,
  perfect: false,
  missed: true,
}

/** Vorschau der Wertung, sofort nach dem letzten Tap. Maßgeblich ist der Wert vom Server. */
export function previewLaunch(c: LaunchChallenge, input: LaunchInput): LaunchResult {
  const taps = input.taps
  if (taps.length === 0 || taps.length > c.maxTaps) return MISSED
  const best = c.zones.map(() => 0)
  let previous = 0
  for (const t of taps) {
    if (!Number.isFinite(t) || t < previous || t > c.windowMs) return MISSED
    previous = t
    const p = needleAt(c, t)
    c.zones.forEach((z, i) => {
      best[i] = Math.max(best[i]!, zoneScore(c, Math.abs(p - z)))
    })
  }
  const hits = best.filter((b) => b > 0).length
  const cores = best.filter((b) => b >= 1).length
  const quality = round3(best.reduce((a, b) => a + b, 0) / c.zones.length)
  return {
    quality,
    hits,
    cores,
    landingX: round3(-1 + FLY_MIN + FLY_RANGE * quality),
    landingY: 0,
    perfect: hits === c.zones.length && quality >= c.perfectQuality,
    missed: hits === 0,
  }
}

/** Urteil in Stufen. „Perfekt!“ gibt es nur für alle drei Kerne. */
export function launchVerdict(l: LaunchResult) {
  if (l.missed) return 'Daneben!'
  if (l.perfect) return 'Perfekt!'
  if (l.hits === 3) return l.quality >= 0.85 ? 'Hervorragend!' : 'Volltreffer!'
  if (l.hits === 2) return l.quality >= 0.6 ? 'Stark!' : 'Gut'
  return l.quality >= 0.3 ? 'Okay' : 'Wackelig'
}

/** Detailzeile, z. B. „2/3 Zonen · 1× Kern · 71 %“. */
export function launchDetail(l: LaunchResult) {
  const parts = [`${l.hits}/3 Zonen`]
  if (l.cores > 0) parts.push(`${l.cores}× Kern`)
  parts.push(`${Math.round(l.quality * 100)} %`)
  return parts.join(' · ')
}

// ---------------------------------------------------------------- Abschussrahmen → Welt

/**
 * Drehung vom Abschussrahmen in Weltkoordinaten (x, z) der Seite. Launcher von A liegt links (θ = π), von B
 * rechts (θ = 0), siehe `LAUNCH_THETA`. +x zeigt jeweils zur Bowl-Mitte.
 */
function frameAngle(side: Side) {
  return side === 'A' ? 0 : Math.PI
}

/** Punkt im Abschussrahmen → Welt (x, z), `unit` = Abstand Launcher–Mitte in Welteinheiten. */
export function frameToWorld(side: Side, p: Vec2, unit: number) {
  const a = frameAngle(side)
  const cos = Math.cos(a)
  const sin = Math.sin(a)
  return { x: (p.x * cos - p.y * sin) * unit, z: (p.x * sin + p.y * cos) * unit }
}
