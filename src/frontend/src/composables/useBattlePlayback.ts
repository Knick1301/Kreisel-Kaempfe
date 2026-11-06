import { markRaw, onUnmounted, ref } from 'vue'
import type { BattleReport, Side } from '@/api'
import { createPose, type Choreography } from '@/game/choreographer'
import { BattleClock } from '@/game/clock'
import type { TimelineEvent, UltType } from '@/api'
import { calloutFor, displayEnergy, isDeflected, playbackRate, type Tone } from '@/game/playback'
import { playEvent, spinHum } from '@/game/sound'

export interface ActiveCallout {
  id: number
  text: string
  tone: Tone
  ult?: UltType
}

const raf: (cb: (now: number) => void) => number =
  globalThis.requestAnimationFrame?.bind(globalThis) ??
  ((cb) => setTimeout(() => cb(performance.now()), 16) as unknown as number)
const cancelRaf: (id: number) => void =
  globalThis.cancelAnimationFrame?.bind(globalThis) ?? ((id) => clearTimeout(id))

/**
 * So oft (ms Echtzeit) bekommt das HUD die neue Zeit. Jede Änderung von `time` rendert die ganze Kampfansicht
 * neu, 60-mal pro Sekunde wäre unnötig teuer. Die 3D-Szene liest `clock.t` direkt und läuft flüssig weiter.
 */
const HUD_STEP_MS = 50

/** Ab dieser Intensität gibt es kurz Slow-Mo. */
const SLOW_MO_HIT = 0.8

/**
 * Wiedergabe eines Kampfs: eine Uhr für Szene, HUD und Textverlauf, Slow-Mo, Einblendungen.
 * `time` ist die reaktive Kopie der Uhr fürs HUD, die 3D-Szene liest `clock.t` direkt.
 */
export function useBattlePlayback() {
  const clock = markRaw(new BattleClock())
  const time = ref(-1)
  const callouts = ref<ActiveCallout[]>([])

  let report: BattleReport | null = null
  let choreo: Choreography | null = null
  let me: Side = 'A'
  let cursor = 0
  let frame = 0
  let last = 0
  let lastHud = -Infinity
  let ended = false
  let calloutId = 0
  let onEnd: () => void = () => {}
  /** Ereignis-Haken, true = selbst behandelt (keine Standard-Einblendung, kein Standard-Ton) */
  let listener: (e: TimelineEvent) => boolean = () => false
  let held = false
  /** Treffer-Einblendungen oben weglassen (die 3D-Szene zeigt sie an der Trefferstelle) */
  let quietHits = false
  // Surren: Posen fürs Panorama und Tempo der Kreisel
  const humPose = { A: createPose(), B: createPose() }
  const humPrev = { A: { x: 0, z: 0, t: -1 }, B: { x: 0, z: 0, t: -1 } }
  const timers = new Set<ReturnType<typeof setTimeout>>()

  function load(
    r: BattleReport,
    c: Choreography | null,
    mySide: Side,
    calm: boolean,
    done: () => void,
  ) {
    report = r
    choreo = c
    me = mySide
    onEnd = done
    calmMode = calm
    applyRate()
    reset()
  }

  let calmMode = false
  function applyRate() {
    const finishMs = choreo?.finishMs ?? report?.durationMs ?? 0
    const ults = (report?.timeline?.events ?? []).filter((e) => e.type === 'ULT')
    const slowMo = [
      ...(choreo?.clashes ?? []).filter((x) => x.intensity >= SLOW_MO_HIT && !x.final),
      ...ults,
    ]
    clock.setRate((t) => playbackRate(slowMo, finishMs, t, calmMode))
  }

  /**
   * Neuen Kampfverlauf übernehmen, ohne die Uhr zurückzusetzen (Ult ausgelöst, der Server hat ab dem Druck
   * neu gerechnet). Bis zur aktuellen Zeit ist der Verlauf identisch, Ereignisse davor gelten als gezeigt.
   */
  function replace(r: BattleReport, c: Choreography | null) {
    report = r
    choreo = c
    applyRate()
    const events = r.timeline?.events ?? []
    cursor = events.findIndex((e) => e.t > clock.t)
    if (cursor < 0) cursor = events.length
    ended = false
  }

  function onEvent(fn: (e: TimelineEvent) => boolean) {
    listener = fn
  }

  /** Treffer-Einblendungen oben aus (true), wenn sie in der Arena an der Trefferstelle erscheinen. */
  function setQuietHits(quiet: boolean) {
    quietHits = quiet
  }

  /** Anhalten, z. B. für die Ult-Einblendung. */
  function hold() {
    if (held || ended) return
    held = true
    clock.pause()
    stop()
    updateHum(true)
  }

  /** Surren an Spin, Tempo und Position anpassen. */
  function updateHum(ducked = false) {
    if (!report?.timeline || !spinHum.running) return
    const radius = choreo?.arena.radius ?? 4
    for (const side of ['A', 'B'] as const) {
      const energy = clock.t < 0 ? 1 : displayEnergy(report, side, clock.t)
      let speed = 0
      let pan = side === 'A' ? -0.4 : 0.4
      if (choreo && clock.t >= 0) {
        const p = humPose[side]
        choreo.sample(side, clock.t, p)
        const prev = humPrev[side]
        const dt = clock.t - prev.t
        if (prev.t >= 0 && dt > 0 && dt < 200) {
          speed = Math.min(1, Math.hypot(p.x - prev.x, p.z - prev.z) / (dt / 1000) / 8)
        }
        prev.x = p.x
        prev.z = p.z
        prev.t = clock.t
        pan = (p.x / radius) * 0.8
      }
      spinHum.update(side, energy, speed, pan, ducked)
    }
  }

  function resume() {
    if (!held) return
    held = false
    if (ended) return
    updateHum(false)
    clock.play()
    last = 0
    frame = raf(tick)
  }

  function endMs() {
    return choreo?.endMs ?? (report?.durationMs ?? 0) + 1500
  }

  function reset() {
    stop()
    clock.pause()
    clock.seek(-1)
    time.value = -1
    lastHud = -Infinity
    held = false
    spinHum.stop()
    cursor = 0
    ended = false
    callouts.value = []
  }

  function tick(now: number) {
    const dt = last ? now - last : 16
    last = now
    clock.advance(dt)
    process()
    updateHum()
    if (now - lastHud >= HUD_STEP_MS) {
      time.value = clock.t
      lastHud = now
    }
    if (!ended && clock.t >= endMs()) {
      ended = true
      time.value = clock.t
      clock.pause()
      spinHum.stop()
      onEnd()
    }
    if (clock.playing) frame = raf(tick)
  }

  function process() {
    const events = report?.timeline?.events ?? []
    while (cursor < events.length && events[cursor]!.t <= clock.t) {
      const e = events[cursor]!
      if (clock.t - e.t < 400 && !listener(e)) {
        const hit = e.type === 'CLASH' || e.type === 'DEFLECT'
        const c = quietHits && hit ? null : calloutFor(events, cursor, me)
        if (c) show(c.text, c.tone, c.ult)
        playEvent(e, e.type === 'CLASH' && isDeflected(events, cursor))
      }
      cursor++
      // Der Haken kann anhalten (Ult-Einblendung): Rest erst nach dem Fortsetzen
      if (held) break
    }
  }

  function show(text: string, tone: Tone, ult?: UltType) {
    const id = ++calloutId
    callouts.value = [...callouts.value.slice(-2), { id, text, tone, ult }]
    const timer = setTimeout(
      () => {
        callouts.value = callouts.value.filter((c) => c.id !== id)
        timers.delete(timer)
      },
      ult ? 1800 : 1300,
    )
    timers.add(timer)
  }

  function start() {
    reset()
    humPrev.A.t = humPrev.B.t = -1
    spinHum.start()
    clock.play(0)
    last = 0
    frame = raf(tick)
  }

  /** Springt ans Ende (Finish plus Ausklang), ohne alle Einblendungen abzufeuern. */
  function skip() {
    const end = endMs()
    const events = report?.timeline?.events ?? []
    cursor = events.length
    clock.seek(end)
    time.value = end
    callouts.value = []
    spinHum.stop()
    if (!ended) {
      ended = true
      clock.pause()
      onEnd()
    }
  }

  function stop() {
    cancelRaf(frame)
    frame = 0
    last = 0
  }

  onUnmounted(() => {
    stop()
    spinHum.stop()
    timers.forEach((t) => clearTimeout(t))
  })

  return {
    clock,
    time,
    callouts,
    load,
    start,
    skip,
    reset,
    stop,
    replace,
    hold,
    resume,
    onEvent,
    setQuietHits,
  }
}
