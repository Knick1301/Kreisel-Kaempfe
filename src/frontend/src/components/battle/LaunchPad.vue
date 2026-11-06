<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import type { LaunchChallenge, LaunchInput, LaunchResult } from '@/api'
import { SIGNAL, launchVerdict, needleAt, previewLaunch, zoneAt } from '@/game/launch'

/**
 * Anspinnen: Ein Zeiger pendelt durch einen schlichten Balken mit drei Zonen. Drei Sekunden, drei Taps
 * (Klick, Finger, Leertaste), jede Zone zählt einmal. Der helle Kern gibt volle Punkte, der Rest der Zone
 * weniger. Gemessen wird ab dem Start des Zeigers, gewertet nur auf dem Server.
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

const now = () => globalThis.performance?.now() ?? Date.now()
const raf: (cb: () => void) => number =
  globalThis.requestAnimationFrame?.bind(globalThis) ??
  ((cb) => setTimeout(cb, 16) as unknown as number)
const cancelRaf: (id: number) => void =
  globalThis.cancelAnimationFrame?.bind(globalThis) ?? ((id) => clearTimeout(id))

type TapKind = 'core' | 'hit' | 'repeat' | 'miss'

interface Tap {
  t: number
  p: number
  zone: number | null
  score: number
  kind: TapKind
}

interface Popup {
  id: number
  p: number
  text: string
  kind: TapKind
}

const POPUP_TEXT: Record<TapKind, string> = {
  core: 'Kern!',
  hit: 'Treffer',
  repeat: 'Schon drin',
  miss: 'Daneben',
}
const POPUP_MS = 750
const reducedMotion = globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false

const t = ref(-LEAD_MS)
const taps = ref<Tap[]>([])
const result = ref<LaunchResult | null>(null)
const popups = ref<Popup[]>([])
const barEl = ref<HTMLElement>()
const zoneEls = ref<HTMLElement[]>([])
/** Ansage für Screenreader nach jedem Tap */
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
/** Bester Wert je Zone bisher */
const best = computed(() =>
  props.challenge.zones.map((_, i) =>
    taps.value.reduce((m, tap) => (tap.zone === i ? Math.max(m, tap.score) : m), 0),
  ),
)
const finished = ref(false)
const hits = computed(() => best.value.filter((b) => b > 0).length)
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

// ---------------------------------------------------------------- Eingabe

function finish() {
  if (done) return
  done = true
  finished.value = true
  const input: LaunchInput = { taps: taps.value.map((x) => x.t) }
  const preview = previewLaunch(props.challenge, input)
  emit('charge', preview.quality)
  // erst kurz „Spin!“, dann das Urteil
  verdictTimer = setTimeout(() => {
    result.value = preview
  }, 350)
  emit('launch', input, preview)
}

function tap(stamp: number) {
  if (done) return
  const ms = Math.round(stamp - t0)
  if (ms < 0 || ms > props.challenge.windowMs) return
  const p = needleAt(props.challenge, ms)
  const hit = zoneAt(props.challenge, p)
  const before = hit ? (best.value[hit.zone] ?? 0) : 0
  // dieselbe Zone nochmal zählt nur, wenn der Tap besser ist (z. B. jetzt im Kern)
  const kind: TapKind = !hit
    ? 'miss'
    : hit.score <= before
      ? 'repeat'
      : hit.score >= 1
        ? 'core'
        : 'hit'
  taps.value = [...taps.value, { t: ms, p, zone: hit?.zone ?? null, score: hit?.score ?? 0, kind }]
  feedback(kind, p, hit?.zone ?? null)
  beep(kind === 'core' ? 1320 : kind === 'hit' ? 880 : kind === 'repeat' ? 440 : 196)
  emit('charge', best.value.reduce((a, b) => a + b, 0) / props.challenge.zones.length)
  if (taps.value.length >= props.challenge.maxTaps) finish()
}

/** Sichtbare Rückmeldung: Text über dem Tap, Zone pulsiert bei Treffer, Balken ruckt bei Fehlern. */
function feedback(kind: TapKind, p: number, zone: number | null) {
  const id = ++popupId
  popups.value = [...popups.value, { id, p, text: POPUP_TEXT[kind], kind }]
  const timer = setTimeout(() => {
    popups.value = popups.value.filter((x) => x.id !== id)
    popupTimers.delete(timer)
  }, POPUP_MS)
  popupTimers.add(timer)
  const left = props.challenge.maxTaps - taps.value.length
  announce.value = `${POPUP_TEXT[kind]}. ${hits.value} von 3 Zonen, noch ${left} ${left === 1 ? 'Tap' : 'Taps'}.`

  if (!animated.value) return
  if ((kind === 'core' || kind === 'hit') && zone !== null) {
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

function onPointerDown(e: PointerEvent) {
  tap(stampOf(e))
}

function onKeyDown(e: KeyboardEvent) {
  if (e.code !== 'Space' && e.key !== ' ' && e.key !== 'Enter') return
  e.preventDefault()
  if (!e.repeat) tap(stampOf(e))
}

function loop() {
  t.value = now() - t0
  if (!done && t.value > props.challenge.windowMs) finish()
  if (!done) frame = raf(loop)
}

// ---------------------------------------------------------------- Klick-Ton

let audio: AudioContext | null = null
function beep(freq: number) {
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
    gain.gain.exponentialRampToValueAtTime(0.09, at + 0.006)
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
  frame = raf(loop)
})

onUnmounted(() => {
  cancelRaf(frame)
  clearTimeout(verdictTimer)
  popupTimers.forEach((x) => clearTimeout(x))
  window.removeEventListener('keydown', onKeyDown)
  void audio?.close().catch(() => undefined)
  emit('charge', 0)
})
</script>

<template>
  <div
    class="launchpad"
    role="application"
    aria-label="Anspinnen: Der Zeiger läuft durch den Balken. Dreimal tippen (Klick, Finger oder Leertaste), wenn er in einer der drei Zonen steht."
    @pointerdown.prevent="onPointerDown"
    @contextmenu.prevent
  >
    <p v-if="banner" :key="banner" class="banner" :class="{ calm }" aria-live="assertive">
      {{ banner }}
    </p>

    <div class="gauge">
      <div class="pips" aria-hidden="true">
        <i
          v-for="n in challenge.maxTaps"
          :key="n"
          :class="taps[n - 1] ? taps[n - 1]!.kind : 'open'"
        />
      </div>

      <div ref="barEl" class="bar" aria-hidden="true">
        <span
          v-for="(z, i) in zonesGeo"
          :key="i"
          ref="zoneEls"
          class="zone"
          :class="{ hit: (best[i] ?? 0) > 0, core: (best[i] ?? 0) >= 1 }"
          :style="z.zone"
        >
          <span class="core" :style="z.core" />
        </span>
        <span
          v-for="(tp, i) in taps"
          :key="`mark-${i}`"
          class="mark"
          :class="tp.kind"
          :style="{ left: pct(tp.p) }"
        />
        <span v-if="!finished" class="needle" :style="{ left: pct(needle) }" />
      </div>

      <span
        v-for="pop in popups"
        :key="pop.id"
        class="popup mono"
        :class="[pop.kind, { still: !animated }]"
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

/* Taps: offen, Treffer, Kern, wiederholt, daneben */
.pips {
  position: absolute;
  right: 0;
  bottom: calc(100% + 10px);
  display: flex;
  gap: 5px;
}

.pips i {
  width: 9px;
  height: 9px;
  border: 1px solid var(--text-muted);
  transition:
    background 0.12s,
    border-color 0.12s;
}

.pips i.hit {
  border-color: var(--text);
  background: rgba(237, 237, 239, 0.55);
}

.pips i.core {
  border-color: var(--text);
  background: var(--text);
  box-shadow: 0 0 8px rgba(237, 237, 239, 0.8);
}

.pips i.repeat {
  border-color: var(--text-faint);
  background: var(--line-strong);
}

.pips i.miss {
  border-color: #e5484d;
  background: rgba(229, 72, 77, 0.35);
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

/* getroffene Zone bleibt klar hervorgehoben */
.zone.hit {
  background: rgba(237, 237, 239, 0.42);
  box-shadow:
    0 0 0 1px var(--text),
    0 0 12px rgba(237, 237, 239, 0.35);
}

.zone.core .core {
  background: var(--text);
  box-shadow: 0 0 10px rgba(237, 237, 239, 0.9);
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

  .pips i {
    transition: none;
  }
}
</style>
