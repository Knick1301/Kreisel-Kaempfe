import { markRaw, onUnmounted, ref } from 'vue'
import type { BattleReport, Side } from '@/api'
import type { Choreography } from '@/game/choreographer'
import { BattleClock } from '@/game/clock'
import { calloutFor, playbackRate, type Tone } from '@/game/playback'

export interface ActiveCallout {
  id: number
  text: string
  tone: Tone
}

const raf: (cb: (now: number) => void) => number =
  globalThis.requestAnimationFrame?.bind(globalThis) ??
  ((cb) => setTimeout(() => cb(performance.now()), 16) as unknown as number)
const cancelRaf: (id: number) => void =
  globalThis.cancelAnimationFrame?.bind(globalThis) ?? ((id) => clearTimeout(id))

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
  let ended = false
  let calloutId = 0
  let onEnd: () => void = () => {}
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
    const finishMs = c?.finishMs ?? r.durationMs ?? 0
    const slowMo = (c?.clashes ?? []).filter((x) => x.intensity >= SLOW_MO_HIT && !x.final)
    clock.setRate((t) => playbackRate(slowMo, finishMs, t, calm))
    reset()
  }

  function endMs() {
    return choreo?.endMs ?? (report?.durationMs ?? 0) + 1500
  }

  function reset() {
    stop()
    clock.pause()
    clock.seek(-1)
    time.value = -1
    cursor = 0
    ended = false
    callouts.value = []
  }

  function tick(now: number) {
    const dt = last ? now - last : 16
    last = now
    clock.advance(dt)
    process()
    if (!ended && clock.t >= endMs()) {
      ended = true
      clock.pause()
      onEnd()
    }
    if (clock.playing) frame = raf(tick)
  }

  function process() {
    time.value = clock.t
    const events = report?.timeline?.events ?? []
    while (cursor < events.length && events[cursor]!.t <= clock.t) {
      const e = events[cursor]!
      if (clock.t - e.t < 400) {
        const c = calloutFor(events, cursor, me)
        if (c) show(c.text, c.tone)
      }
      cursor++
    }
  }

  function show(text: string, tone: Tone) {
    const id = ++calloutId
    callouts.value = [...callouts.value.slice(-2), { id, text, tone }]
    const timer = setTimeout(() => {
      callouts.value = callouts.value.filter((c) => c.id !== id)
      timers.delete(timer)
    }, 1300)
    timers.add(timer)
  }

  function start() {
    reset()
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
    timers.forEach((t) => clearTimeout(t))
  })

  return { clock, time, callouts, load, start, skip, reset, stop }
}
