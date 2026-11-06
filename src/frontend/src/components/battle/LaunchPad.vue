<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import type { LaunchChallenge, LaunchInput, LaunchResult } from '@/api'
import {
  PULL_FULL_PX,
  SIGNAL,
  SPEED_FULL_PX_S,
  launchVerdict,
  needleAt,
  previewLaunch,
  pullFactor,
  zoneAt,
} from '@/game/launch'

/**
 * Anspinnen mit Reißleine: Ein Zeiger pendelt durch einen Balken mit drei Zonen. Einmal greifen (gedrückt
 * halten), nach hinten ziehen und im richtigen Moment loslassen. Gewertet werden der Moment des Loslassens
 * (Zone, heller Kern = voll), wie weit gezogen wurde und wie schnell der letzte Ruck war. Die Zugrichtung
 * bestimmt, wohin der Kreisel fliegt. Tastatur: Leertaste halten (spannt), loslassen; Pfeiltasten geben die
 * Richtung. Gemessen wird ab dem Start des Zeigers, gewertet nur auf dem Server.
 */
const props = withDefaults(defineProps<{ challenge: LaunchChallenge; calm?: boolean }>(), {
  calm: false,
})
const emit = defineEmits<{
  /** Schwung 0–1 für die 3D-Szene (Kreisel dreht schneller) */
  charge: [charge: number]
  launch: [input: LaunchInput, preview: LaunchResult]
}>()

const LEAD_MS = 900
/** So weit muss man ziehen, damit das Loslassen zählt (px). Kürzer: kein Start, nochmal greifen. */
const MIN_PULL = 28
/** Ab dieser seitlichen Zuglänge (px) ist die Richtung voll links/rechts. */
const AIM_PX = 110
/** Zeitfenster für das Tempo des letzten Rucks */
const SPEED_WINDOW_MS = 90
/** Tastatur: so lange halten bis zur vollen Spannung; Tempo ist mit der Taste fest */
const KEY_FULL_MS = 900
const KEY_SPEED = 0.6

const now = () => globalThis.performance?.now() ?? Date.now()
const raf: (cb: () => void) => number =
  globalThis.requestAnimationFrame?.bind(globalThis) ??
  ((cb) => setTimeout(cb, 16) as unknown as number)
const cancelRaf: (id: number) => void =
  globalThis.cancelAnimationFrame?.bind(globalThis) ?? ((id) => clearTimeout(id))

type Kind = 'core' | 'hit' | 'miss'

interface Release {
  p: number
  kind: Kind
}

interface Popup {
  id: number
  p: number
  text: string
  kind: Kind | 'short'
}

const POPUP_MS = 750
const reducedMotion = globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false

const t = ref(-LEAD_MS)
const released = ref<Release | null>(null)
const result = ref<LaunchResult | null>(null)
const popups = ref<Popup[]>([])
interface Sample {
  x: number
  y: number
  t: number
}
/** Laufender Zug: Startpunkt, aktueller Punkt und die letzten Positionen fürs Tempo */
const grab = ref<{
  id: number
  x0: number
  y0: number
  x: number
  y: number
  samples: Sample[]
} | null>(null)
const padEl = ref<HTMLElement>()
/** Tastatur: gehaltene Pfeiltasten und Beginn des Haltens */
const keyAim = ref(0)
const keyStart = ref<number | null>(null)
const barEl = ref<HTMLElement>()
const zoneEls = ref<HTMLElement[]>([])
/** Ansage für Screenreader */
const announce = ref('')
let popupId = 0
const popupTimers = new Set<ReturnType<typeof setTimeout>>()
let t0 = 0
let frame = 0
let done = false
let verdictTimer: ReturnType<typeof setTimeout> | undefined

const needle = computed(() =>
  needleAt(props.challenge, Math.max(0, Math.min(t.value, props.challenge.windowMs))),
)
const finished = ref(false)
const animated = computed(() => !props.calm && !reducedMotion)

const banner = computed(() => {
  if (result.value) return launchVerdict(result.value)
  if (finished.value) return SIGNAL
  if (t.value < 0) return 'Bereit'
  return null
})

const pct = (v: number) => `${(Math.max(0, Math.min(1, v)) * 100).toFixed(2)}%`
const zonesGeo = computed(() =>
  props.challenge.zones.map((z) => ({
    zone: { left: pct(z - props.challenge.zoneHalf), width: pct(2 * props.challenge.zoneHalf) },
    core: {
      left: pct(
        (props.challenge.zoneHalf - props.challenge.coreHalf) / (2 * props.challenge.zoneHalf),
      ),
      width: pct(props.challenge.coreHalf / props.challenge.zoneHalf),
    },
  })),
)

// ---------------------------------------------------------------- Spannung und Tempo

/** Zuglänge 0–1 gerade (Maus/Finger oder gehaltene Taste) */
const tension = computed(() => {
  const g = grab.value
  if (g) return Math.min(1, Math.hypot(g.x - g.x0, g.y - g.y0) / PULL_FULL_PX)
  if (keyStart.value !== null) return Math.min(1, (t.value + t0 - keyStart.value) / KEY_FULL_MS)
  return 0
})

/** Tempo 0–1 aus den letzten Positionen */
function speedOf(samples: Sample[], at: number) {
  const recent = samples.filter((s) => at - s.t <= SPEED_WINDOW_MS)
  if (recent.length < 2) return 0
  const a = recent[0]!
  const b = recent[recent.length - 1]!
  const dt = Math.max(16, b.t - a.t) / 1000
  return Math.min(1, Math.hypot(b.x - a.x, b.y - a.y) / dt / SPEED_FULL_PX_S)
}
const liveSpeed = ref(0)

// ---------------------------------------------------------------- Eingabe

function finish(input: LaunchInput) {
  if (done) return
  done = true
  finished.value = true
  grab.value = null
  keyStart.value = null
  const preview = previewLaunch(props.challenge, input)
  emit('charge', preview.quality)
  // erst kurz „Spin!“, dann das Urteil
  verdictTimer = setTimeout(() => {
    result.value = preview
  }, 350)
  emit('launch', input, preview)
}

/** Loslassen: Zeitpunkt, Zuglänge, Tempo und Richtung gehen an den Server. */
function release(stamp: number, pull: number, speed: number, aim: number) {
  if (done) return
  const ms = Math.round(stamp - t0)
  if (ms < 0 || ms > props.challenge.windowMs) return
  const p = needleAt(props.challenge, ms)
  const hit = zoneAt(props.challenge, p)
  const kind: Kind = !hit ? 'miss' : hit.score >= 1 ? 'core' : 'hit'
  released.value = { p, kind }
  const round = (v: number) => Math.round(v * 1000) / 1000
  const input: LaunchInput = {
    taps: [ms],
    aim: round(aim),
    pull: round(pull),
    speed: round(speed),
  }
  feedback(kind, p, hit?.zone ?? null)
  beep(kind === 'core' ? 1320 : kind === 'hit' ? 880 : 196)
  finish(input)
}

const POPUP_TEXT: Record<Kind | 'short', string> = {
  core: 'Kern!',
  hit: 'Treffer',
  miss: 'Daneben',
  short: 'Weiter ziehen!',
}

function popup(kind: Kind | 'short', p: number) {
  const id = ++popupId
  popups.value = [...popups.value, { id, p, text: POPUP_TEXT[kind], kind }]
  const timer = setTimeout(() => {
    popups.value = popups.value.filter((x) => x.id !== id)
    popupTimers.delete(timer)
  }, POPUP_MS)
  popupTimers.add(timer)
}

/** Sichtbare Rückmeldung: Text über dem Zeiger, Zone pulsiert bei Treffer, Balken ruckt bei Fehlern. */
function feedback(kind: Kind, p: number, zone: number | null) {
  popup(kind, p)
  announce.value = POPUP_TEXT[kind]
  if (!animated.value) return
  if (kind !== 'miss' && zone !== null) {
    zoneEls.value[zone]?.animate?.(
      [
        { transform: 'scaleY(2.4)', filter: 'brightness(2.2)' },
        { transform: 'scaleY(1)', filter: 'brightness(1)' },
      ],
      { duration: 420, easing: 'cubic-bezier(0.2, 0.8, 0.2, 1)' },
    )
  } else {
    barEl.value?.animate?.(
      [
        { transform: 'translateX(0)' },
        { transform: 'translateX(-7px)' },
        { transform: 'translateX(6px)' },
        { transform: 'translateX(-3px)' },
        { transform: 'translateX(0)' },
      ],
      { duration: 240, easing: 'ease-out' },
    )
  }
}

// Zeitstempel der Events nutzen (gleiche Uhr wie performance.now), nicht den nächsten Frame
const stampOf = (e: Event) => {
  const n = now()
  return e.timeStamp > 0 && Math.abs(e.timeStamp - n) < 1000 ? e.timeStamp : n
}

function local(e: PointerEvent) {
  const r = padEl.value?.getBoundingClientRect()
  return { x: e.clientX - (r?.left ?? 0), y: e.clientY - (r?.top ?? 0) }
}

function onPointerDown(e: PointerEvent) {
  if (done || grab.value) return
  const p = local(e)
  const at = stampOf(e)
  grab.value = { id: e.pointerId, x0: p.x, y0: p.y, x: p.x, y: p.y, samples: [{ ...p, t: at }] }
  ;(e.currentTarget as Element | null)?.setPointerCapture?.(e.pointerId)
  beep(330, 0.04)
}

function onPointerMove(e: PointerEvent) {
  const g = grab.value
  if (!g || g.id !== e.pointerId) return
  const p = local(e)
  const at = stampOf(e)
  g.x = p.x
  g.y = p.y
  g.samples.push({ ...p, t: at })
  if (g.samples.length > 40) g.samples.splice(0, g.samples.length - 40)
  liveSpeed.value = speedOf(g.samples, at)
  emit('charge', tension.value * 0.8)
}

function onPointerUp(e: PointerEvent) {
  const g = grab.value
  if (!g || g.id !== e.pointerId) return
  grab.value = null
  const at = stampOf(e)
  const dx = g.x - g.x0
  const len = Math.hypot(dx, g.y - g.y0)
  if (len < MIN_PULL) {
    popup('short', needle.value)
    emit('charge', 0)
    return
  }
  release(
    at,
    Math.min(1, len / PULL_FULL_PX),
    speedOf(g.samples, at),
    Math.max(-1, Math.min(1, dx / AIM_PX)),
  )
}

function onPointerCancel(e: PointerEvent) {
  if (grab.value?.id === e.pointerId) grab.value = null
}

const isPull = (e: KeyboardEvent) => e.code === 'Space' || e.key === ' ' || e.key === 'Enter'

function onKeyDown(e: KeyboardEvent) {
  if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
    e.preventDefault()
    keyAim.value = e.key === 'ArrowLeft' ? -1 : 1
    return
  }
  if (!isPull(e)) return
  e.preventDefault()
  if (e.repeat || done || keyStart.value !== null) return
  keyStart.value = stampOf(e)
  beep(330, 0.04)
}

function onKeyUp(e: KeyboardEvent) {
  if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
    keyAim.value = 0
    return
  }
  if (!isPull(e) || keyStart.value === null) return
  e.preventDefault()
  const at = stampOf(e)
  const pull = Math.min(1, (at - keyStart.value) / KEY_FULL_MS)
  keyStart.value = null
  release(at, pull, KEY_SPEED, keyAim.value)
}

/** Leine fürs Overlay: vom Griffpunkt zur aktuellen Position, gespannt ab MIN_PULL. */
const cord = computed(() => {
  const g = grab.value
  if (!g) return null
  const len = Math.hypot(g.x - g.x0, g.y - g.y0)
  return { x0: g.x0, y0: g.y0, x: g.x, y: g.y, taut: len >= MIN_PULL }
})
const holding = computed(() => !!grab.value || keyStart.value !== null)
/** Vorschau des Zugfaktors während des Ziehens */
const livePull = computed(() =>
  pullFactor(tension.value, keyStart.value !== null ? KEY_SPEED : liveSpeed.value),
)

function loop() {
  t.value = now() - t0
  // Tempo klingt ab, wenn die Hand stillhält
  const g = grab.value
  if (g) liveSpeed.value = speedOf(g.samples, now())
  else if (keyStart.value !== null) emit('charge', tension.value * 0.8)
  if (!done && t.value > props.challenge.windowMs) finish({ taps: [] })
  if (!done) frame = raf(loop)
}

// ---------------------------------------------------------------- Klick-Ton

let audio: AudioContext | null = null
function beep(freq: number, gainLevel = 0.09) {
  try {
    const Ctx =
      globalThis.AudioContext ??
      (globalThis as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
    if (!Ctx) return
    audio ??= new Ctx()
    const at = audio.currentTime
    const osc = audio.createOscillator()
    const gain = audio.createGain()
    osc.frequency.value = freq
    gain.gain.setValueAtTime(0.0001, at)
    gain.gain.exponentialRampToValueAtTime(gainLevel, at + 0.006)
    gain.gain.exponentialRampToValueAtTime(0.0001, at + 0.14)
    osc.connect(gain).connect(audio.destination)
    osc.start(at)
    osc.stop(at + 0.16)
  } catch {
    audio = null
  }
}

onMounted(() => {
  t0 = now() + LEAD_MS
  window.addEventListener('keydown', onKeyDown)
  window.addEventListener('keyup', onKeyUp)
  frame = raf(loop)
})

onUnmounted(() => {
  cancelRaf(frame)
  clearTimeout(verdictTimer)
  popupTimers.forEach((x) => clearTimeout(x))
  window.removeEventListener('keydown', onKeyDown)
  window.removeEventListener('keyup', onKeyUp)
  void audio?.close().catch(() => undefined)
  emit('charge', 0)
})
</script>

<template>
  <div
    ref="padEl"
    class="launchpad"
    :class="{ holding }"
    role="application"
    aria-label="Anspinnen: Der Zeiger läuft durch den Balken. Gedrückt halten, weit und schnell nach hinten ziehen und loslassen, wenn er in einer Zone steht. Tastatur: Leertaste halten zum Spannen, loslassen; Pfeiltasten geben die Richtung."
    @pointerdown.prevent="onPointerDown"
    @pointermove="onPointerMove"
    @pointerup="onPointerUp"
    @pointercancel="onPointerCancel"
    @contextmenu.prevent
  >
    <svg v-if="cord" class="cord" aria-hidden="true">
      <circle :cx="cord.x0" :cy="cord.y0" r="6" />
      <line :x1="cord.x0" :y1="cord.y0" :x2="cord.x" :y2="cord.y" :class="{ taut: cord.taut }" />
      <circle :cx="cord.x" :cy="cord.y" r="9" class="handle" :class="{ taut: cord.taut }" />
    </svg>
    <p v-if="!finished && t >= 0 && !holding" class="howto">
      Halten · weit und schnell ziehen · in der Zone loslassen
    </p>
    <p v-if="banner" :key="banner" class="banner" :class="{ calm }" aria-live="assertive">
      {{ banner }}
    </p>

    <div class="gauge">
      <div class="meters" aria-hidden="true">
        <span class="meter">
          <span class="label">Zug</span>
          <span class="track"><span :style="{ transform: `scaleX(${tension})` }" /></span>
        </span>
        <span class="meter">
          <span class="label">Ruck</span>
          <span class="track">
            <span :style="{ transform: `scaleX(${keyStart !== null ? 0.6 : liveSpeed})` }" />
          </span>
        </span>
        <span class="meter power mono" :class="{ full: livePull >= 0.97 }">
          {{ holding ? `${Math.round(livePull * 100)} %` : '' }}
        </span>
      </div>

      <div ref="barEl" class="bar" aria-hidden="true">
        <span v-for="(z, i) in zonesGeo" :key="i" ref="zoneEls" class="zone" :style="z.zone">
          <span class="core" :style="z.core" />
        </span>
        <span
          v-if="released"
          class="mark"
          :class="released.kind"
          :style="{ left: pct(released.p) }"
        />
        <span v-if="!finished" class="needle" :style="{ left: pct(needle) }" />
      </div>

      <span
        v-for="pop in popups"
        :key="pop.id"
        class="popup mono"
        :class="[pop.kind === 'short' ? 'repeat' : pop.kind, { still: !animated }]"
        :style="{ left: pct(pop.p) }"
        aria-hidden="true"
      >
        {{ pop.text }}
      </span>
    </div>

    <p class="sr-only" aria-live="polite">{{ announce }}</p>
  </div>
</template>

<style scoped>
.launchpad {
  position: absolute;
  inset: 0;
  z-index: 2;
  cursor: pointer;
  touch-action: none;
  user-select: none;
  -webkit-user-select: none;
  outline: none;
}

.cord {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  overflow: visible;
  pointer-events: none;
}

.cord circle {
  fill: var(--text-muted);
}

.cord line {
  stroke: var(--text-muted);
  stroke-width: 2;
  stroke-dasharray: 5 4;
}

.cord line.taut {
  stroke: var(--text);
  stroke-dasharray: none;
}

.cord .handle {
  fill: none;
  stroke: var(--text-muted);
  stroke-width: 2;
}

.cord .handle.taut {
  stroke: var(--text);
  fill: rgba(237, 237, 239, 0.25);
}

.launchpad.holding {
  cursor: grabbing;
}

.howto {
  position: absolute;
  bottom: calc(18% + 48px);
  left: 50%;
  margin: 0;
  font-size: 0.8rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-muted);
  transform: translateX(-50%);
  pointer-events: none;
  white-space: nowrap;
}

.banner {
  position: absolute;
  top: 30%;
  left: 50%;
  margin: 0;
  font-size: clamp(2.6rem, 7vw, 5rem);
  font-weight: 600;
  letter-spacing: -0.04em;
  color: var(--text);
  white-space: nowrap;
  transform: translate(-50%, -50%);
  animation: fade 0.3s ease-out;
  pointer-events: none;
}

.banner.calm {
  animation: none;
}

@keyframes fade {
  from {
    opacity: 0;
  }
}

.gauge {
  position: absolute;
  bottom: 18%;
  left: 50%;
  width: min(560px, calc(100% - 32px));
  transform: translateX(-50%);
  pointer-events: none;
}

.bar {
  position: relative;
  height: 14px;
  border: 1px solid var(--line-strong);
  border-radius: 2px;
  background: var(--bg);
}

/* Zug und Ruck: live, solange man zieht */
.meters {
  position: absolute;
  right: 0;
  bottom: calc(100% + 10px);
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 0.7rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.meter {
  display: flex;
  align-items: center;
  gap: 6px;
}

.meter .track {
  position: relative;
  width: 70px;
  height: 5px;
  background: var(--line);
  overflow: hidden;
}

.meter .track span {
  position: absolute;
  inset: 0;
  background: var(--text);
  transform-origin: left;
}

.meter.power {
  min-width: 3.5em;
  color: var(--text);
  font-size: 0.8rem;
}

.meter.power.full {
  text-shadow: 0 0 8px rgba(237, 237, 239, 0.9);
}

.mark {
  position: absolute;
  top: -4px;
  bottom: -4px;
  width: 2px;
  margin-left: -1px;
  background: var(--text);
}

.mark.miss {
  background: #e5484d;
}

.mark.repeat {
  background: var(--text-faint);
}

.mark.core {
  box-shadow: 0 0 8px rgba(237, 237, 239, 0.9);
}

.popup {
  position: absolute;
  bottom: calc(100% + 10px);
  font-size: 0.78rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  white-space: nowrap;
  color: var(--text);
  transform: translateX(-50%);
  animation: rise 0.75s ease-out forwards;
}

.popup.miss {
  color: #e5484d;
}

.popup.repeat {
  color: var(--text-muted);
}

.popup.still {
  animation: none;
}

@keyframes rise {
  0% {
    opacity: 0;
    transform: translate(-50%, 6px);
  }
  15% {
    opacity: 1;
    transform: translate(-50%, 0);
  }
  70% {
    opacity: 1;
  }
  100% {
    opacity: 0;
    transform: translate(-50%, -14px);
  }
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

.zone {
  position: absolute;
  top: 0;
  bottom: 0;
  background: rgba(237, 237, 239, 0.14);
}

.zone .core {
  position: absolute;
  top: 0;
  bottom: 0;
  background: rgba(237, 237, 239, 0.55);
}

.zone {
  transform-origin: center;
}

.needle {
  position: absolute;
  top: -5px;
  bottom: -5px;
  width: 2px;
  margin-left: -1px;
  background: var(--text);
}

@media (prefers-reduced-motion: reduce) {
  .banner,
  .popup {
    animation: none;
  }
}
</style>
