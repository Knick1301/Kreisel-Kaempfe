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
/** Seitliche Ablenkung bei voller Zugrichtung wie `AIM_RANGE` auf dem Server. */
const AIM_RANGE = 0.35

export const MISSED: LaunchResult = {
  quality: 0,
  hits: 0,
  cores: 0,
  landingX: -1 + FLY_MIN,
  landingY: 0,
  perfect: false,
  missed: true,
}

/** Zugfaktor wie auf dem Server: PULL_BASE + PULL_LENGTH · Zuglänge + PULL_SPEED · Tempo. */
export const PULL_BASE = 0.45
export const PULL_LENGTH = 0.35
export const PULL_SPEED = 0.2
/** So viele Pixel Zug ergeben volle Länge, so viele Pixel pro Sekunde volles Tempo. */
export const PULL_FULL_PX = 220
export const SPEED_FULL_PX_S = 2200

const clampAim = (v: number | undefined) => (Number.isFinite(v) ? Math.max(-1, Math.min(1, v!)) : 0)

const unit = (v: number | undefined) => (Number.isFinite(v) ? Math.max(0, Math.min(1, v!)) : 0)

export function pullFactor(pull: number, speed: number) {
  return PULL_BASE + PULL_LENGTH * unit(pull) + PULL_SPEED * unit(speed)
}

/** Vorschau der Wertung, sofort nach dem Loslassen. Maßgeblich ist der Wert vom Server. */
export function previewLaunch(c: LaunchChallenge, input: LaunchInput): LaunchResult {
  const taps = input.taps
  if (taps.length !== c.maxTaps) return MISSED
  const t = taps[0]!
  if (!Number.isFinite(t) || t < 0 || t > c.windowMs) return MISSED
  const p = needleAt(c, t)
  const timing = Math.max(0, ...c.zones.map((z) => zoneScore(c, Math.abs(p - z))))
  if (timing <= 0) return MISSED
  const quality = round3(timing * pullFactor(input.pull ?? 0, input.speed ?? 0))
  const cores = timing >= 1 ? 1 : 0
  return {
    quality,
    hits: 1,
    cores,
    landingX: round3(-1 + FLY_MIN + FLY_RANGE * quality),
    landingY: round3(AIM_RANGE * clampAim(input.aim)),
    perfect: cores >= 1 && quality >= c.perfectQuality,
    missed: false,
  }
}

/** Urteil in Stufen. „Perfekt!“ gibt es nur im Kern mit vollem, schnellem Zug. */
export function launchVerdict(l: LaunchResult) {
  if (l.missed) return 'Daneben!'
  if (l.perfect) return 'Perfekt!'
  if (l.quality >= 0.85) return 'Hervorragend!'
  if (l.quality >= 0.7) return 'Stark!'
  if (l.quality >= 0.5) return 'Gut'
  return 'Wackelig'
}

/** Detailzeile, z. B. „Kern · 92 %“. */
export function launchDetail(l: LaunchResult) {
  if (l.missed) return 'keine Zone · 0 %'
  return `${l.cores > 0 ? 'Kern' : 'Zone'} · ${Math.round(l.quality * 100)} %`
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
