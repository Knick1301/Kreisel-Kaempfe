import type { BattleReport, FighterDto, FinishType, Side } from '@/api'
import {
  arenaById,
  bowlSlope,
  bowlY,
  CONTACT_DIST,
  playRadius,
  pocketFloorY,
  wrapAngle,
  type ArenaDefinition,
} from './arenas'
import { frameToWorld } from './launch'
import { range, seededStream } from './prng'
import { clamp, displayEnergy, isDeflected, other, smoothstep } from './playback'

/**
 * Deterministischer Choreograf: erzeugt aus Seed + Timeline die Bahnen beider Kreisel.
 *
 * Keine Physik-Engine, sondern geplante Bewegung: Jeder Kreisel kreist auf einer geseedeten Bahn,
 * vor jedem CLASH wird die Bahn so verbogen, dass sich beide exakt zum Event-Zeitpunkt am Treffpunkt
 * berühren. Danach prallen sie ab und kehren auf ihre Bahn zurück. Gleiche Eingabe ergibt identische
 * Bahnen (Replay), unterschiedliche Seeds sehen unterschiedlich aus.
 *
 * Alles wird einmal vorab mit 120 Hz berechnet. `sample()` interpoliert nur noch und schreibt in ein
 * übergebenes Objekt, damit im Render-Loop nichts alloziert wird.
 */

export const CHOREO_HZ = 120
/** Wie lange nach dem Finish noch animiert wird (Slow-Mo, Kamerafahrt). */
export const AFTERMATH_MS = 3200
/** Höhe, aus der die Kreisel beim Launch fallen (auch die Pose während des Countdowns). */
export const LAUNCH_HEIGHT = 0.7
/** Flugzeit vom Launcher zum Landepunkt der Schleuder (s). */
export const LAUNCH_FLY_S = 0.55
/** Höhe des Flugbogens über der direkten Linie. */
const LAUNCH_ARC = 0.9

const DT = 1 / CHOREO_HZ
const SAMPLES_PER_MS = CHOREO_HZ / 1000
const GRAVITY = 14
/** Mitte-Mitte-Radius, bis zu dem normale Clashes höchstens stattfinden. */
const MAX_CLASH_R = 2.2
const APPROACH_S = 0.65
const RETURN_S = 0.9
const BOUNCE = 0.55
/** Ruhehöhe der drei Teile über der Spitze (Wheel, Track, Tip). */
export const PART_HEIGHTS = [0.5, 0.3, 0.1] as const

export interface FighterPose {
  x: number
  y: number
  z: number
  /** Neigung um die x-Achse (Spitze nach +z) */
  tiltX: number
  /** Neigung um die z-Achse (Spitze nach -x) */
  tiltZ: number
  /** Drehwinkel um die eigene Achse */
  spin: number
  /** true, sobald der Kreisel gebürstet ist: Pose bleibt am Ursprung, die Teile fliegen einzeln */
  burst: boolean
}

/** Versatz eines Teils gegenüber seiner Ruheposition (Welt-Achsen) plus Eigenrotation. */
export interface PartPose {
  x: number
  y: number
  z: number
  rx: number
  ry: number
  rz: number
}

export interface ClashMark {
  t: number
  x: number
  y: number
  z: number
  /** Stoßrichtung vom Angreifer zum Verteidiger */
  nx: number
  nz: number
  intensity: number
  deflected: boolean
  attacker: Side
  final: boolean
}

/** Aufsetzen nach dem Schleuder-Flug (für Funken und Aufschlagring). */
export interface LandingMark {
  t: number
  side: Side
  x: number
  y: number
  z: number
  /** Flugrichtung */
  nx: number
  nz: number
}

export interface Choreography {
  readonly arena: ArenaDefinition
  readonly finishMs: number
  readonly endMs: number
  readonly finish: FinishType
  readonly winner: Side
  readonly loser: Side
  readonly clashes: readonly ClashMark[]
  readonly burst: { t: number; x: number; y: number; z: number } | null
  /** Landepunkte der Schleuder, nach Zeit sortiert (leer ohne Schleuder) */
  readonly landings?: readonly LandingMark[]
  sample(side: Side, tMs: number, out: FighterPose): FighterPose
  /** Schreibt die Teile-Posen (Wheel, Track, Tip) des Burst-Opfers, false vor dem Burst. */
  sampleParts(tMs: number, out: PartPose[]): boolean
}

export const createPose = (): FighterPose => ({
  x: 0,
  y: 0,
  z: 0,
  tiltX: 0,
  tiltZ: 0,
  spin: 0,
  burst: false,
})

export const createPartPoses = (): PartPose[] =>
  [0, 1, 2].map(() => ({ x: 0, y: 0, z: 0, rx: 0, ry: 0, rz: 0 }))

interface Track {
  x: Float32Array
  y: Float32Array
  z: Float32Array
  tx: Float32Array
  tz: Float32Array
  spin: Float32Array
}

interface Params {
  rMean: number
  rAmp: number
  f1: number
  f2: number
  p1: number
  p2: number
  omega: number
  weight: number
}

const SIDES: readonly Side[] = ['A', 'B']

function fighterParams(f: FighterDto | undefined, rng: () => number): Params {
  const st = f?.style ?? { attack: 1 / 3, defense: 1 / 3, stamina: 1 / 3 }
  const dash = (f?.stats.dash ?? 5) / 8
  return {
    // Verteidiger halten die Mitte, Angreifer fegen weit über die Bowl.
    rMean: clamp(
      0.9 * st.defense + 1.55 * st.stamina + 2.05 * st.attack + range(rng, -0.15, 0.15),
      0.8,
      2.1,
    ),
    rAmp: 0.3 + 0.55 * st.attack + range(rng, 0, 0.15),
    f1: range(rng, 0.35, 0.6) * 2 * Math.PI,
    f2: range(rng, 0.9, 1.4) * 2 * Math.PI,
    p1: rng() * 2 * Math.PI,
    p2: rng() * 2 * Math.PI,
    omega: 1.6 + 0.9 * dash + range(rng, -0.15, 0.15),
    weight: f?.stats.weight ?? 79,
  }
}

const bounceNorm = (() => {
  const a = 0.06
  const b = 0.4
  const t = a * Math.log(1 + b / a)
  return (1 - Math.exp(-t / a)) * Math.exp(-t / b)
})()

/** Abprall-Verlauf: 0 im Kontaktmoment, schnell nach außen, dann zurück. */
function bounceShape(tau: number) {
  return ((1 - Math.exp(-tau / 0.06)) * Math.exp(-tau / 0.4)) / bounceNorm
}

/** Weiche Begrenzung des Radius: unterhalb von `soft` unverändert, darüber asymptotisch bis `hard`. */
function softLimit(r: number, soft: number, hard: number) {
  if (r <= soft) return r
  const span = hard - soft
  return soft + span * Math.tanh((r - soft) / span)
}

/** Startradius beim Launch (auch für die Wartestellung vor dem Anspinnen). */
export function launchRadius(arena: ArenaDefinition) {
  return Math.min(3.25, playRadius(arena) - 0.05)
}

/** Tatsächlicher Abstand zur Mitte im ersten Bild (Startradius nach der weichen Randbegrenzung). */
export function launchRestRadius(arena: ArenaDefinition) {
  const play = playRadius(arena)
  return softLimit(launchRadius(arena), play - 0.35, play)
}

/**
 * Feste Startwinkel bei Kämpfen mit Anspinnen: A links, B rechts aus Sicht der Broadcast-Kamera. Genau dort
 * warten die Kreisel vor dem Anspinnen, so geht die Wartestellung ohne Sprung in den Kampf über.
 */
export const LAUNCH_THETA: Readonly<Record<Side, number>> = { A: Math.PI, B: 0 }

export function createChoreography(report: BattleReport): Choreography {
  const tl = report.timeline
  if (!tl || !report.winnerSide) throw new Error('Kampf ohne Timeline')
  const arena = arenaById(report.arena?.id)
  const events = tl.events
  const finishMs = report.durationMs ?? events[events.length - 1]!.t
  const endMs = finishMs + AFTERMATH_MS
  const n = Math.round(endMs * SAMPLES_PER_MS) + 1
  const kFinish = Math.round(finishMs * SAMPLES_PER_MS)
  const winner = report.winnerSide
  const loser = other(winner)
  const finish = report.finish
  const rng = seededStream(report.seed ?? report.id, 1)
  const play = playRadius(arena)
  const soft = play - 0.35
  // Treffpunkte bleiben innerhalb der weichen Randbegrenzung, sonst wäre der Kontakt nicht exakt
  const maxClashR = Math.min(MAX_CLASH_R, soft - CONTACT_DIST / 2 - 0.05)

  const params: Record<Side, Params> = {
    A: fighterParams(report.fighters?.[0], rng),
    B: fighterParams(report.fighters?.[1], rng),
  }
  const dir = rng() < 0.5 ? 1 : -1

  // ---------------------------------------------------------------- Energie (für Tempo, Spin, Taumeln)
  const energy: Record<Side, Float32Array> = { A: new Float32Array(n), B: new Float32Array(n) }
  for (const s of SIDES) {
    for (let k = 0; k < n; k++) energy[s][k] = displayEnergy(report, s, k / SAMPLES_PER_MS)
  }

  // ---------------------------------------------------------------- freie Bahn (polar)
  const r: Record<Side, Float64Array> = { A: new Float64Array(n), B: new Float64Array(n) }
  const th: Record<Side, Float64Array> = { A: new Float64Array(n), B: new Float64Array(n) }
  // Zufallswerte immer ziehen, damit der restliche Verlauf für alte Kämpfe gleich bleibt
  const theta0 = rng() * 2 * Math.PI
  const offsetB = Math.PI + range(rng, -0.4, 0.4)
  const launched = !!report.fighters?.some((f) => f.launch)
  const start: Record<Side, number> = launched
    ? { ...LAUNCH_THETA }
    : { A: theta0, B: theta0 + offsetB }
  const launchR = launchRadius(arena)
  const tornadoR = Math.min(arena.ridge.r - 0.05, soft - 0.02)
  // Schleuder: Der Kreisel fliegt vom Launcher zu seinem Landepunkt und startet dort.
  const unit = launchRestRadius(arena)
  const landing: Record<Side, { x: number; z: number } | null> = { A: null, B: null }
  const startR: Record<Side, number> = { A: launchR, B: launchR }
  const fly: Record<Side, number> = { A: 0, B: 0 }
  report.fighters?.forEach((f) => {
    const l = f.launch
    if (l?.landingX == null || l.landingY == null) return
    const w = frameToWorld(f.side, { x: l.landingX, y: l.landingY }, unit)
    landing[f.side] = w
    start[f.side] = Math.atan2(w.z, w.x)
    startR[f.side] = clamp(Math.hypot(w.x, w.z), 0.3, soft - 0.05)
    fly[f.side] = LAUNCH_FLY_S
  })
  for (const s of SIDES) {
    const p = params[s]
    let theta = start[s]
    for (let k = 0; k < n; k++) {
      const t = k * DT
      const e = energy[s][k]!
      const battleR =
        p.rMean + p.rAmp * (0.7 * Math.sin(p.f1 * t + p.p1) + 0.3 * Math.sin(p.f2 * t + p.p2))
      let radius: number
      let omega = p.omega
      if (t < fly[s]) {
        // noch in der Luft: die Bahn beginnt erst am Landepunkt
        radius = startR[s]
        omega = 0
      } else if (t < 1) {
        const u = (t - fly[s]) / (1 - fly[s])
        radius = startR[s] + (tornadoR - startR[s]) * smoothstep(u)
        omega *= 1.3
      } else if (t < 3.5) {
        radius = tornadoR + 0.05 * Math.sin(2.1 * t + p.p1)
        omega *= 1.25
      } else {
        const b = smoothstep((t - 3.5) / 1.2)
        radius = tornadoR + (battleR - tornadoR) * b
        omega *= 1.25 - 0.25 * b
        // Langsame Kreisel sinken zur Mitte.
        radius *= 0.55 + 0.45 * Math.min(1, e / 0.35)
      }
      omega *= 0.35 + 0.65 * Math.sqrt(e)
      if (k > 0) theta += dir * omega * DT
      r[s][k] = clamp(radius, 0.3, play - 0.05)
      th[s][k] = theta
    }
  }

  // ---------------------------------------------------------------- Clashes exakt treffen
  const bx: Record<Side, Float64Array> = { A: new Float64Array(n), B: new Float64Array(n) }
  const bz: Record<Side, Float64Array> = { A: new Float64Array(n), B: new Float64Array(n) }
  const kicks: { k: number; side: Side; x: number; z: number; mag: number }[] = []
  const clashMarks: ClashMark[] = []
  const finalKo = finish === 'BURST' || finish === 'RING_OUT'
  const launchEnd = Math.round(0.45 * CHOREO_HZ)
  let prevK = launchEnd
  let finalNormal = { x: 1, z: 0, plus: loser as Side, intensity: 0.5 }

  const posX = (s: Side, k: number) => r[s][k]! * Math.cos(th[s][k]!) + bx[s][k]!
  const posZ = (s: Side, k: number) => r[s][k]! * Math.sin(th[s][k]!) + bz[s][k]!

  events.forEach((e, index) => {
    if (e.type !== 'CLASH') return
    const kc = Math.round(e.t * SAMPLES_PER_MS)
    if (kc <= prevK || kc >= n) return
    const isFinal = finalKo && e.t === finishMs
    const ringOut = isFinal && finish === 'RING_OUT'
    const deflected = isDeflected(events, index)
    const att = e.attacker
    const def = other(att)

    const rc = ringOut
      ? play - 0.55 - CONTACT_DIST / 2
      : clamp(
          (Math.hypot(posX('A', kc), posZ('A', kc)) + Math.hypot(posX('B', kc), posZ('B', kc))) / 2,
          0.85,
          maxClashR,
        )
    const mx = rc * Math.cos(e.angle)
    const mz = rc * Math.sin(e.angle)
    let nx: number
    let nz: number
    // Seite, die auf +n liegt: beim Ring-Out das Opfer (außen), sonst der Verteidiger.
    let plus: Side
    if (ringOut) {
      nx = Math.cos(e.angle)
      nz = Math.sin(e.angle)
      plus = loser
    } else {
      // Stoßrichtung folgt der Anordnung vor dem Anlauf, damit niemand die Seite wechseln muss.
      const k0 = Math.max(prevK, kc - Math.round(APPROACH_S * CHOREO_HZ))
      let baseX = posX(def, k0) - posX(att, k0)
      let baseZ = posZ(def, k0) - posZ(att, k0)
      const len = Math.hypot(baseX, baseZ)
      if (len > 1e-3) {
        baseX /= len
        baseZ /= len
      } else {
        baseX = -Math.sin(e.angle) * dir
        baseZ = Math.cos(e.angle) * dir
      }
      const beta = range(rng, -0.35, 0.35)
      nx = baseX * Math.cos(beta) - baseZ * Math.sin(beta)
      nz = baseX * Math.sin(beta) + baseZ * Math.cos(beta)
      plus = def
    }
    if (isFinal) finalNormal = { x: nx, z: nz, plus, intensity: e.intensity }

    for (const s of SIDES) {
      const sign = s === plus ? 1 : -1
      const targetX = mx + sign * nx * (CONTACT_DIST / 2) - bx[s][kc]!
      const targetZ = mz + sign * nz * (CONTACT_DIST / 2) - bz[s][kc]!
      const targetR = Math.hypot(targetX, targetZ)
      const targetTh = Math.atan2(targetZ, targetX)

      // Winkel: über die ganze Lücke seit dem letzten Clash verteilen, bleibt danach bestehen.
      const dTh = wrapAngle(targetTh - th[s][kc]!)
      const span = kc - prevK
      for (let k = prevK + 1; k < n; k++) {
        th[s][k] = th[s][k]! + dTh * (k >= kc ? 1 : smoothstep((k - prevK) / span))
      }

      // Radius: kurzer Anlauf vor dem Treffer, danach zurück auf die eigene Bahn.
      const dR = targetR - r[s][kc]!
      const kStart = Math.max(prevK, kc - Math.round(APPROACH_S * CHOREO_HZ))
      const kEnd = Math.min(n, kc + Math.round(RETURN_S * CHOREO_HZ))
      for (let k = kStart + 1; k < kEnd; k++) {
        const w =
          k <= kc
            ? smoothstep((k - kStart) / (kc - kStart))
            : 1 - smoothstep((k - kc) / (kEnd - kc))
        r[s][k] = r[s][k]! + dR * w
      }

      // Abprall: stärker für den Leichteren; wer abgewehrt wird, fliegt weiter zurück.
      if (isFinal && s === loser) continue
      const self = params[s].weight
      const opp = params[other(s)].weight
      const role = s === att ? (deflected ? 1.5 : 0.6) : deflected ? 0.5 : 1.4
      const amp = BOUNCE * (0.3 + e.intensity) * Math.sqrt(opp / self) * role
      for (let k = kc + 1; k < n; k++) {
        const f = bounceShape((k - kc) * DT)
        if (f < 1e-4 && k > kc + CHOREO_HZ) break
        bx[s][k] = bx[s][k]! + sign * nx * amp * f
        bz[s][k] = bz[s][k]! + sign * nz * amp * f
      }
      kicks.push({
        k: kc,
        side: s,
        x: sign * nx,
        z: sign * nz,
        mag: 0.22 * (0.3 + e.intensity) * role,
      })
    }

    clashMarks.push({
      t: e.t,
      x: mx,
      y: bowlY(arena, rc) + 0.35,
      z: mz,
      nx: plus === def ? nx : -nx,
      nz: plus === def ? nz : -nz,
      intensity: e.intensity,
      deflected,
      attacker: att,
      final: isFinal,
    })
    prevK = kc
  })

  // ---------------------------------------------------------------- kartesisch zusammensetzen
  const track: Record<Side, Track> = {
    A: emptyTrack(n),
    B: emptyTrack(n),
  }
  const landingMarks: LandingMark[] = []
  for (const s of SIDES) {
    const tr = track[s]
    for (let k = 0; k < n; k++) {
      let x = posX(s, k)
      let z = posZ(s, k)
      const rad = Math.hypot(x, z)
      const lim = softLimit(rad, soft, play)
      if (lim !== rad) {
        x *= lim / rad
        z *= lim / rad
      }
      tr.x[k] = x
      tr.z[k] = z
    }
    // Flug vom Launcher (Ruheposition der Wartestellung) zum Landepunkt
    const kFly = Math.round(fly[s] * CHOREO_HZ)
    if (landing[s] && kFly > 0) {
      const fromX = unit * Math.cos(LAUNCH_THETA[s])
      const fromZ = unit * Math.sin(LAUNCH_THETA[s])
      const toX = tr.x[kFly]!
      const toZ = tr.z[kFly]!
      for (let k = 0; k < kFly; k++) {
        // gleichmäßig über den Boden, der Bogen kommt aus der Höhe: wirkt wie ein Wurf
        const u = k / kFly
        const e = u * (1.15 - 0.15 * u)
        tr.x[k] = fromX + (toX - fromX) * e
        tr.z[k] = fromZ + (toZ - fromZ) * e
      }
      const len = Math.hypot(toX - fromX, toZ - fromZ) || 1
      landingMarks.push({
        t: fly[s] * 1000,
        side: s,
        x: toX,
        y: bowlY(arena, Math.hypot(toX, toZ)),
        z: toZ,
        nx: (toX - fromX) / len,
        nz: (toZ - fromZ) / len,
      })
    }
  }

  // ---------------------------------------------------------------- Finish des Verlierers
  const lt = track[loser]
  const wt = track[winner]
  let burst: Choreography['burst'] = null
  let parts: Float32Array[] | null = null
  let ringOutLand = Infinity
  let ringOutDir = { x: 1, z: 0 }

  if (finish === 'SPIN_OUT' || finish === 'DECISION') {
    const tau0 = finish === 'SPIN_OUT' ? 0.55 : 0.9
    slowDown(lt, kFinish, n, tau0)
    if (finish === 'DECISION') slowDown(wt, kFinish, n, 2.5)
  } else if (finish === 'RING_OUT') {
    const ringEvent = [...events].reverse().find((e) => e.type === 'RING_OUT')
    const angle =
      ringEvent?.type === 'RING_OUT' ? ringEvent.angle : Math.atan2(finalNormal.z, finalNormal.x)
    const sx = lt.x[kFinish]!
    const sz = lt.z[kFinish]!
    const sy = bowlY(arena, Math.hypot(sx, sz))
    const landR = arena.radius + arena.pocketDepth * 0.5
    const tx = landR * Math.cos(angle)
    const tz = landR * Math.sin(angle)
    const ty = pocketFloorY(arena) + 0.02
    const flight = 0.75 - 0.15 * finalNormal.intensity
    const vy = (ty - sy + 0.5 * GRAVITY * flight * flight) / flight
    const skid = 0.22
    ringOutDir = { x: Math.cos(angle), z: Math.sin(angle) }
    ringOutLand = kFinish + Math.round(flight * CHOREO_HZ)
    for (let k = kFinish + 1; k < n; k++) {
      const tau = (k - kFinish) * DT
      if (tau <= flight) {
        const u = tau / flight
        lt.x[k] = sx + (tx - sx) * u
        lt.z[k] = sz + (tz - sz) * u
        lt.y[k] = sy + vy * tau - 0.5 * GRAVITY * tau * tau
      } else {
        const after = tau - flight
        const slide = skid * (1 - Math.exp(-after / 0.15))
        lt.x[k] = tx + ringOutDir.x * slide
        lt.z[k] = tz + ringOutDir.z * slide
        lt.y[k] = ty + 0.12 * Math.abs(Math.sin(after * 14)) * Math.exp(-after / 0.12)
      }
    }
  } else if (finish === 'BURST') {
    const ox = lt.x[kFinish]!
    const oz = lt.z[kFinish]!
    const oy = bowlY(arena, Math.hypot(ox, oz))
    burst = { t: finishMs, x: ox, y: oy, z: oz }
    for (let k = kFinish + 1; k < n; k++) {
      lt.x[k] = ox
      lt.z[k] = oz
    }
    const outX = finalNormal.plus === loser ? finalNormal.x : -finalNormal.x
    const outZ = finalNormal.plus === loser ? finalNormal.z : -finalNormal.z
    parts = simulateParts(arena, rng, ox, oy, oz, outX, outZ, finalNormal.intensity, n - kFinish)
  }

  // Nicht ineinander fahren (außer im Moment des Kontakts).
  const sepWeight = new Float32Array(n).fill(1)
  for (const c of clashMarks) {
    const kc = Math.round(c.t * SAMPLES_PER_MS)
    for (let k = Math.max(0, kc - 24); k <= Math.min(n - 1, kc + 12); k++) {
      const span = k < kc ? 24 : 12
      sepWeight[k] = Math.min(sepWeight[k]!, smoothstep(Math.abs(k - kc) / span))
    }
  }
  // Die Seitenordnung (wer links, wer rechts) bleibt stetig: Kreuzen sich die freien Bahnen,
  // drücken sich die Kreisel weg, statt durcheinander durchzuspringen.
  const minD = CONTACT_DIST * 0.97
  // Nach Burst/Ring-Out ist der Verlierer nicht mehr in der Bowl, dann endet die Trennung beim Finish.
  const sepEnd = finalKo ? kFinish : n
  let ux = 1
  let uz = 0
  for (let k = 0; k < sepEnd; k++) {
    const dx = track.B.x[k]! - track.A.x[k]!
    const dz = track.B.z[k]! - track.A.z[k]!
    const d = Math.hypot(dx, dz)
    const w = sepWeight[k]!
    const proj = dx * ux + dz * uz
    if (k === 0 || w <= 0 || proj >= minD) {
      if (d > 1e-6) {
        ux = dx / d
        uz = dz / d
      }
      continue
    }
    const push = ((minD - proj) / 2) * w
    track.A.x[k] = track.A.x[k]! - ux * push
    track.A.z[k] = track.A.z[k]! - uz * push
    track.B.x[k] = track.B.x[k]! + ux * push
    track.B.z[k] = track.B.z[k]! + uz * push
    const ndx = track.B.x[k]! - track.A.x[k]!
    const ndz = track.B.z[k]! - track.A.z[k]!
    const nd = Math.hypot(ndx, ndz)
    if (nd > 1e-6) {
      ux = ndx / nd
      uz = ndz / nd
    }
  }

  // ---------------------------------------------------------------- Höhe, Neigung, Spin
  for (const s of SIDES) {
    const tr = track[s]
    const isLoser = s === loser
    let psi = rng() * 2 * Math.PI
    let spin = rng() * 2 * Math.PI
    let fallX = 1
    let fallZ = 0
    for (let k = 0; k < n; k++) {
      const t = k * DT
      const e = energy[s][k]!
      const after = isLoser && k > kFinish ? (k - kFinish) * DT : -1
      const flying = isLoser && finish === 'RING_OUT' && k > kFinish

      if (!flying) {
        const rad = Math.hypot(tr.x[k]!, tr.z[k]!)
        let hop = 0
        const land = fly[s] || 0.22
        if (t < land) {
          const u = t / land
          // aus dem Launcher fallen, mit Schleuder in einem flachen Bogen
          hop = LAUNCH_HEIGHT * (1 - u * u) + (fly[s] ? LAUNCH_ARC * 4 * u * (1 - u) : 0)
        } else if (t < land + 0.2) hop = 0.07 * Math.sin((Math.PI * (t - land)) / 0.2)
        tr.y[k] = bowlY(arena, rad) + hop
      }

      // In die Kurve legen (Querbeschleunigung aus der Bahn).
      let lx = 0
      let lz = 0
      if (k > 0 && k < n - 1 && !flying && !(burst && isLoser && k >= kFinish)) {
        const ax = (tr.x[k + 1]! - 2 * tr.x[k]! + tr.x[k - 1]!) / (DT * DT)
        const az = (tr.z[k + 1]! - 2 * tr.z[k]! + tr.z[k - 1]!) / (DT * DT)
        const mag = Math.hypot(ax, az)
        if (mag > 1e-6) {
          const lean = Math.min(0.2, mag * 0.016)
          lx = (ax / mag) * lean
          lz = (az / mag) * lean
        }
      }

      // Präzession und Nutation: je weniger Energie, desto stärker.
      const weak = 1 - e
      psi += (5 + 9 * weak) * DT * (after > 0 ? Math.exp(-after / 1.2) : 1)
      let wob = 0.012 + 0.32 * weak ** 2.4
      wob += 0.012 * weak * Math.sin(29 * t)

      // Stöße kippen kurz weg vom Treffer.
      for (const kick of kicks) {
        if (kick.side !== s || kick.k > k) continue
        const tau = (k - kick.k) * DT
        if (tau > 1) continue
        const f = Math.exp(-tau / 0.18) * Math.min(1, tau / 0.03)
        lx += kick.x * kick.mag * f
        lz += kick.z * kick.mag * f
      }

      if (after < 0) {
        fallX = Math.cos(psi)
        fallZ = Math.sin(psi)
      }
      let tiltDirX = lx + Math.cos(psi) * wob
      let tiltDirZ = lz + Math.sin(psi) * wob

      if (after >= 0) {
        if (finish === 'BURST') {
          tiltDirX = 0
          tiltDirZ = 0
        } else if (finish === 'RING_OUT') {
          // im Flug nach vorn kippen, in der Tasche umfallen
          const land = (ringOutLand - kFinish) * DT
          const lean = 0.2 + 0.7 * smoothstep(after / land)
          const lie = after < land ? 0 : smoothstep((after - land) / 0.9)
          tiltDirX = ringOutDir.x * (lean * (1 - lie) + 1.15 * lie)
          tiltDirZ = ringOutDir.z * (lean * (1 - lie) + 1.15 * lie)
        } else {
          const lie = 1.15 * smoothstep(after / (finish === 'DECISION' ? 2.2 : 1.4))
          const keep = 1 - smoothstep(after / 0.8)
          tiltDirX = tiltDirX * keep + fallX * lie
          tiltDirZ = tiltDirZ * keep + fallZ * lie
        }
      }

      tr.tx[k] = tiltDirZ
      tr.tz[k] = -tiltDirX

      if (!(burst && isLoser && k > kFinish)) {
        const lying = after >= 0 && finish !== 'BURST' ? smoothstep(after / 1.2) : 0
        spin -= (4 + 30 * e ** 0.7) * (1 - lying) * DT
      }
      tr.spin[k] = spin
    }
  }

  const kAt = (tMs: number) => clamp(tMs * SAMPLES_PER_MS, 0, n - 1)

  return {
    arena,
    finishMs,
    endMs,
    finish,
    winner,
    loser,
    clashes: clashMarks,
    burst,
    landings: landingMarks.sort((a, b) => a.t - b.t),
    sample(side, tMs, out) {
      const tr = track[side]
      if (tMs < 0) {
        out.x = tr.x[0]!
        out.y = tr.y[0]!
        out.z = tr.z[0]!
        out.tiltX = 0
        out.tiltZ = 0
        out.spin = tr.spin[0]!
        out.burst = false
        return out
      }
      const f = kAt(tMs)
      const i = Math.floor(f)
      const j = Math.min(n - 1, i + 1)
      const u = f - i
      out.x = lerp(tr.x, i, j, u)
      out.y = lerp(tr.y, i, j, u)
      out.z = lerp(tr.z, i, j, u)
      out.tiltX = lerp(tr.tx, i, j, u)
      out.tiltZ = lerp(tr.tz, i, j, u)
      out.spin = lerp(tr.spin, i, j, u)
      out.burst = burst !== null && side === loser && tMs >= finishMs
      return out
    },
    sampleParts(tMs, out) {
      if (!burst || !parts || tMs < finishMs) return false
      const f = clamp((tMs - finishMs) * SAMPLES_PER_MS, 0, parts[0]!.length / 6 - 1)
      const i = Math.floor(f)
      const u = f - i
      for (let p = 0; p < 3; p++) {
        const a = parts[p]!
        const o = out[p]!
        const i0 = i * 6
        const i1 = Math.min(a.length / 6 - 1, i + 1) * 6
        o.x = a[i0]! + (a[i1]! - a[i0]!) * u
        o.y = a[i0 + 1]! + (a[i1 + 1]! - a[i0 + 1]!) * u
        o.z = a[i0 + 2]! + (a[i1 + 2]! - a[i0 + 2]!) * u
        o.rx = a[i0 + 3]! + (a[i1 + 3]! - a[i0 + 3]!) * u
        o.ry = a[i0 + 4]! + (a[i1 + 4]! - a[i0 + 4]!) * u
        o.rz = a[i0 + 5]! + (a[i1 + 5]! - a[i0 + 5]!) * u
      }
      return true
    },
  }
}

function emptyTrack(n: number): Track {
  return {
    x: new Float32Array(n),
    y: new Float32Array(n),
    z: new Float32Array(n),
    tx: new Float32Array(n),
    tz: new Float32Array(n),
    spin: new Float32Array(n),
  }
}

function lerp(a: Float32Array, i: number, j: number, u: number) {
  return a[i]! + (a[j]! - a[i]!) * u
}

/** Bewegung ab `from` exponentiell abbremsen (Kreisel rollt aus). */
function slowDown(tr: Track, from: number, n: number, tau: number) {
  const ox = tr.x.slice()
  const oz = tr.z.slice()
  let x = ox[from]!
  let z = oz[from]!
  for (let k = from + 1; k < n; k++) {
    const decay = Math.exp(-((k - from) * DT) / tau)
    x += (ox[k]! - ox[k - 1]!) * decay
    z += (oz[k]! - oz[k - 1]!) * decay
    tr.x[k] = x
    tr.z[k] = z
  }
}

/**
 * Flugbahnen der drei Teile nach einem Burst: Impuls weg vom Treffer, Schwerkraft, Aufprall auf der
 * Bowl mit Dämpfung, Rutschen zur Mitte, Rand als Bande. Ergebnis pro Teil: [dx, dy, dz, rx, ry, rz] je Sample,
 * relativ zur Ruheposition des Teils.
 */
function simulateParts(
  arena: ArenaDefinition,
  rng: () => number,
  ox: number,
  oy: number,
  oz: number,
  outX: number,
  outZ: number,
  intensity: number,
  count: number,
): Float32Array[] {
  const result: Float32Array[] = []
  const wall = arena.radius - 0.2
  for (let p = 0; p < 3; p++) {
    const data = new Float32Array(count * 6)
    const rest = PART_HEIGHTS[p]!
    let x = ox
    let y = oy + rest
    let z = oz
    const speed = (2 + 2.5 * intensity) * range(rng, 0.6, 1.2)
    const lateral = range(rng, -1.4, 1.4)
    let vx = outX * speed - outZ * lateral
    let vz = outZ * speed + outX * lateral
    let vy = p === 2 ? range(rng, 1.5, 2.5) : range(rng, 2.5, 4.2)
    let rx = 0
    let ry = 0
    let rz = 0
    let wx = range(rng, 4, 11) * (rng() < 0.5 ? -1 : 1)
    const wy = range(rng, 6, 14)
    let wz = range(rng, 4, 11) * (rng() < 0.5 ? -1 : 1)
    for (let k = 0; k < count; k++) {
      if (k > 0) {
        vy -= GRAVITY * DT
        x += vx * DT
        y += vy * DT
        z += vz * DT
        rx += wx * DT
        ry += wy * DT * Math.exp(-k * DT)
        rz += wz * DT

        let rad = Math.hypot(x, z)
        if (rad > wall) {
          const ux = x / rad
          const uz = z / rad
          const radial = vx * ux + vz * uz
          if (radial > 0) {
            vx -= 1.4 * radial * ux
            vz -= 1.4 * radial * uz
          }
          x = ux * wall
          z = uz * wall
          rad = wall
        }
        const floor = bowlY(arena, rad) + 0.03
        if (y <= floor) {
          y = floor
          if (vy < 0) vy = Math.abs(vy) < 0.6 ? 0 : -vy * 0.32
          // auf dem Boden: zur Mitte rutschen, Reibung, flach legen
          const slope = bowlSlope(arena, rad)
          if (rad > 1e-3) {
            vx -= (x / rad) * GRAVITY * slope * 0.6 * DT
            vz -= (z / rad) * GRAVITY * slope * 0.6 * DT
          }
          const friction = Math.exp(-2.5 * DT)
          vx *= friction
          vz *= friction
          wx *= 0.9
          wz *= 0.9
          const settle = 1 - Math.exp(-DT / 0.12)
          rx += (Math.round(rx / Math.PI) * Math.PI - rx) * settle
          rz += (Math.round(rz / Math.PI) * Math.PI - rz) * settle
        }
      }
      const o = k * 6
      data[o] = x - ox
      data[o + 1] = y - (oy + rest)
      data[o + 2] = z - oz
      data[o + 3] = rx
      data[o + 4] = ry
      data[o + 5] = rz
    }
    result.push(data)
  }
  return result
}
