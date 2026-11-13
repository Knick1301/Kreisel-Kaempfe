<script setup lang="ts">
import { computed, defineAsyncComponent, markRaw, onUnmounted, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ARCHETYPE_LABEL,
  createArenaRoom,
  fetchArenaRoom,
  fetchBeyblades,
  FINISH_LABEL,
  launchArenaRoom,
  type ArenaRoomDto,
  type BattleReport,
  type BeybladeDto,
  type FighterDto,
  type LaunchInput,
  type Side,
  type TimelineEvent,
  type UltType,
} from '@/api'
import { pickActive, setActiveId } from '@/activeBeyblade'
import { refreshMe } from '@/stores/me'
import { hasWebGL } from '@/webgl'
import { arenaById } from '@/game/arenas'
import { BattleClock } from '@/game/clock'
import type { Choreography } from '@/game/choreographer'
import { createStandbyChoreography, type StandbyChoreography } from '@/game/standby'
import { formatClock } from '@/game/playback'
import {
  playCountdown,
  playEvent,
  playUltCutIn,
  playUltReady,
  prepareSounds,
  setSoundMuted,
  soundMuted,
  spinHum,
} from '@/game/sound'
import { rotateInput, type CameraView } from '@/game/physics/controls'
import { LiveChoreography } from '@/game/physics/liveChoreography'
import { RemoteArena, type ArenaEvent } from '@/game/physics/remoteArena'
import BeybladeSpinner from '@/components/BeybladeSpinner.vue'
import LeagueBadge from '@/components/LeagueBadge.vue'
import FighterPanel from '@/components/battle/FighterPanel.vue'
import LaunchPad from '@/components/battle/LaunchPad.vue'
import UltBadge from '@/components/battle/UltBadge.vue'
import UltCutIn from '@/components/battle/UltCutIn.vue'
import TouchControls from '@/components/battle/TouchControls.vue'

const BattleArena = defineAsyncComponent(() => import('@/components/three/battle/BattleArena.vue'))

/**
 * Arena-Modus: Echtzeit-Physik auf dem Server, gesteuert mit WASD, Shift (Dash) und Leertaste/Q (Ult) bzw.
 * per Touch. Gegen die KI, gegen Spieler (Spielersuche, Lobby, Freunde) und im Solo-Cup. Der Browser schickt nur
 * Eingaben und zeigt den Stand an; gewertet wird auf dem Server, im eigenen Arena-Rang.
 */

type Phase =
  | 'loading'
  | 'select'
  | 'creating'
  | 'intro'
  | 'launch'
  | 'waiting'
  | 'countdown'
  | 'fight'
  | 'result'
  | 'error'

const route = useRoute()
const router = useRouter()

const phase = ref<Phase>('loading')
const error = ref<string | null>(null)
const beyblades = ref<BeybladeDto[]>([])
const selectedId = ref<number | null>(null)
const selected = computed(() => beyblades.value.find((b) => b.id === selectedId.value) ?? null)
const room = shallowRef<ArenaRoomDto | null>(null)
const report = shallowRef<BattleReport | null>(null)
const choreo = shallowRef<Choreography | null>(null)
const countdown = ref<string | null>(null)

const reducedMotion = globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
const webgl = hasWebGL()
const clock = new BattleClock()

const mySide = computed<Side>(() => room.value?.yourSide ?? 'A')
const theirSide = computed<Side>(() => (mySide.value === 'A' ? 'B' : 'A'))
const me = computed<FighterDto | undefined>(
  () => room.value?.fighters[mySide.value === 'A' ? 0 : 1],
)
const them = computed<FighterDto | undefined>(
  () => room.value?.fighters[mySide.value === 'A' ? 1 : 0],
)
const pvp = computed(() => room.value?.mode === 'PVP')
const isCup = computed(() => room.value?.cup === true || route.query.cup === '1')

// ---------------------------------------------------------------- HUD (aus dem Server-Stand)

interface Hud {
  t: number
  spin: Record<Side, number>
  meter: Record<Side, number>
  hits: Record<Side, number>
  ult: Record<Side, UltType | null>
  ultReady: boolean
  /** 0–1: eigener Dash wieder bereit */
  dash: number
}
const hud = ref<Hud>({
  t: 0,
  spin: { A: 1, B: 1 },
  meter: { A: 0, B: 0 },
  hits: { A: 0, B: 0 },
  ult: { A: null, B: null },
  ultReady: false,
  dash: 1,
})

const result = computed(() => room.value?.result ?? null)
const winnerFlag = (side: Side) =>
  phase.value === 'result' && result.value ? result.value.winner === side : null

// ---------------------------------------------------------------- Laden, Raum anlegen, Raum öffnen

let run = 0

async function init() {
  run++
  stopLive()
  room.value = null
  error.value = null
  phase.value = 'loading'
  const roomParam = typeof route.query.room === 'string' ? route.query.room : null
  try {
    if (roomParam) {
      openRoom(await fetchArenaRoom(roomParam))
      return
    }
    const list = await fetchBeyblades()
    beyblades.value = Array.isArray(list) ? list : []
    selectedId.value = pickActive(beyblades.value)?.id ?? null
    phase.value = 'select'
  } catch (e) {
    fail(e)
  }
}

function fail(e: unknown) {
  error.value = e instanceof Error ? e.message : String(e)
  phase.value = 'error'
}

/** Neuer Kampf gegen einen zufälligen KI-Gegner. */
async function draw() {
  const bey = selected.value
  if (!bey || phase.value === 'creating') return
  const token = ++run
  stopLive()
  setActiveId(bey.id)
  error.value = null
  phase.value = 'creating'
  try {
    const r = await createArenaRoom(bey.id)
    if (token !== run) return
    openRoom(r)
  } catch (e) {
    if (token !== run) return
    error.value = e instanceof Error ? e.message : String(e)
    phase.value = 'select'
  }
}

/** Raum anzeigen und verbinden. Wer neu lädt, landet je nach Stand beim Anspinnen, Warten oder im Kampf. */
function openRoom(r: ArenaRoomDto) {
  room.value = r
  events = [{ t: 0, type: 'LAUNCH' }]
  energy = { A: [], B: [] }
  report.value = makeReport(r)
  standby = markRaw(createStandbyChoreography(r.arena.id))
  choreo.value = standby
  live = null
  clock.seek(-1)
  connect(r.id)
  if (r.status === 'DONE') phase.value = 'result'
  else if (r.status === 'LAUNCH') phase.value = r.youLaunched ? 'waiting' : 'intro'
  else phase.value = 'countdown'
}

/** Bericht nur für die Darstellung: Kämpfer, Arena und ein mitwachsender Verlauf. */
function makeReport(r: ArenaRoomDto): BattleReport {
  return {
    id: 0,
    winner: '',
    loser: '',
    winnerBeyblade: '',
    loserBeyblade: '',
    finish: 'DECISION',
    rounds: 0,
    playedAt: '',
    mode: r.mode,
    arena: r.arena,
    fighters: r.fighters,
    timeline: { sampleMs: SAMPLE_MS, energy, hits: { A: 0, B: 0 }, events },
  }
}

// ---------------------------------------------------------------- Anspinnen

let standby: StandbyChoreography | null = null

function onCharge(charge: number) {
  standby?.setCharge(mySide.value, charge)
}

async function onLaunch(input: LaunchInput) {
  const r = room.value
  if (!r) return
  phase.value = 'waiting'
  try {
    room.value = await launchArenaRoom(r.id, input)
  } catch (e) {
    fail(e)
  }
}

// ---------------------------------------------------------------- Verbindung und Live-Darstellung

const SAMPLE_MS = 50
let energy: Record<Side, number[]> = { A: [], B: [] }
let events: TimelineEvent[] = []
let remote: RemoteArena | null = null
let live: LiveChoreography | null = null
let raf = 0
let lastCount: number | null = null
let ultReadyPlayed = false
let resultShown = false

function connect(roomId: string) {
  remote?.close()
  const rem = new RemoteArena(roomId)
  remote = rem
  rem.onRoom = (r) => {
    if (remote !== rem) return
    room.value = r
  }
  rem.onEvents = (evs) => {
    if (remote !== rem) return
    ensureLive()
    handle(evs)
  }
  rem.onClose = () => {
    if (remote !== rem || phase.value === 'result') return
    // kurz weg (WLAN, Tab): neu verbinden, der Server wartet ein paar Sekunden
    setTimeout(() => {
      if (remote === rem && phase.value !== 'result') rem.connect()
    }, 800)
  }
  rem.connect()
  resultShown = false
  cancelAnimationFrame(raf)
  raf = requestAnimationFrame(frame)
}

function stopLive() {
  cancelAnimationFrame(raf)
  raf = 0
  remote?.close()
  remote = null
  spinHum.stop()
  cutIn.value = null
  countdown.value = null
}

/** Ab dem Kampf zeigt die Szene die Server-Physik statt der Wartestellung. */
function ensureLive() {
  const st = remote?.latest
  if (live || !remote || !room.value || !st || (st.status !== 'FIGHT' && st.status !== 'DONE'))
    return
  live = markRaw(new LiveChoreography(arenaById(room.value.arena.id), remote))
  standby = null
  choreo.value = live
  clock.play(0)
  countdown.value = null
  phase.value = 'fight'
  playEvent({ t: 0, type: 'LAUNCH' }, false)
  void spinHum.start()
}

function frame(now: number) {
  const rem = remote
  if (!rem) return
  const st = rem.latest
  if (st) {
    if (st.status === 'COUNTDOWN') {
      if (phase.value !== 'countdown') {
        phase.value = 'countdown'
        standby?.setCharge(mySide.value, 1)
        standby?.setCharge(theirSide.value, 1)
      }
      const n = Math.max(1, Math.ceil(st.countdownMs / 1000))
      if (n !== lastCount) {
        lastCount = n
        countdown.value = String(n)
        playCountdown(false)
      }
    }
    if (st.status === 'FIGHT' || st.status === 'DONE') ensureLive()
  }
  if (live) {
    const t = rem.update(now)
    clock.seek(Math.max(clock.t, t))
    record(t)
    sendInput()
    updateHud(rem, t)
    updateHum(rem)
    if (live.finishMs < Number.POSITIVE_INFINITY && clock.t >= live.finishMs) {
      spinHum.stop()
      // Ausklingen lassen (Burst-Teile, Umkippen), dann das Ergebnis zeigen
      if (!resultShown && now - (finishSeenAt ??= now) > 2600) void showResult()
    }
  }
  raf = requestAnimationFrame(frame)
}
let finishSeenAt: number | undefined

async function showResult() {
  resultShown = true
  const r = room.value
  if (r && !r.result) {
    try {
      room.value = await fetchArenaRoom(r.id)
    } catch {
      // Ergebnis kommt dann nicht, die Anzeige zeigt nur Sieg/Niederlage aus der Physik
    }
  }
  phase.value = 'result'
  void refreshMe()
}

function record(t: number) {
  const i = Math.floor(t / SAMPLE_MS)
  if (!remote) return
  for (const side of ['A', 'B'] as const) {
    const arr = energy[side]
    const v = remote.bodies[side].spin
    while (arr.length < i) arr.push(arr.length ? arr[arr.length - 1]! : v)
    arr[i] = v
  }
}

function toTimeline(e: ArenaEvent): TimelineEvent | null {
  switch (e.type) {
    case 'CLASH':
      return {
        t: e.t,
        type: 'CLASH',
        attacker: e.side,
        intensity: e.intensity,
        angle: Math.atan2(e.z, e.x),
        ult: e.ult ?? undefined,
      }
    case 'ULT':
      return {
        t: e.t,
        type: 'ULT',
        side: e.side,
        ult: e.ult!,
        intensity: 1,
        durationMs: e.ult === 'SMASH' ? undefined : ULT_DURATION_MS,
      }
    case 'ULT_END':
      return { t: e.t, type: 'ULT_END', side: e.side, ult: e.ult! }
    case 'FINISH': {
      const loser: Side = e.side === 'A' ? 'B' : 'A'
      if (e.finish === 'DECISION') return { t: e.t, type: 'TIMEOUT' }
      if (e.finish === 'RING_OUT') {
        const b = remote?.bodies[loser]
        return {
          t: e.t,
          type: 'RING_OUT',
          victim: loser,
          angle: b ? Math.atan2(b.z, b.x) : 0,
          pocket: 0,
        }
      }
      return { t: e.t, type: e.finish === 'BURST' ? 'BURST' : 'SPIN_OUT', victim: loser }
    }
    default:
      return null
  }
}

/** Wirkdauer der Ults (Schild, Ansturm, Zweiter Wind) wie auf dem Server */
const ULT_DURATION_MS = 4500

function handle(evs: ArenaEvent[]) {
  live?.absorb(evs)
  let ultChanged = false
  for (const e of evs) {
    const tl = toTimeline(e)
    if (tl) {
      events.push(tl)
      if (tl.type === 'ULT' || tl.type === 'ULT_END') ultChanged = true
      playEvent(tl, e.type === 'CLASH' && e.deflected)
    }
    if (e.type === 'WALL' && e.intensity > 0.3) {
      playEvent(
        { t: e.t, type: 'CLASH', attacker: e.side, intensity: e.intensity * 0.3, angle: 0 },
        false,
      )
    }
    if (e.type === 'ULT' && e.ult) showCutIn(e.side, e.ult)
    if (e.type === 'FINISH') spinHum.stop()
  }
  // Ult-Effekte der Szene hängen am Verlauf: neuer Bericht, damit sie es mitbekommen
  if (ultChanged && room.value) report.value = makeReport(room.value)
}

/** HUD nur ~15× pro Sekunde neu zeichnen: Jedes Update rendert Panels und Ult-Ring neu */
const HUD_MS = 66
let lastHud = -Infinity

function updateHud(rem: RemoteArena, t: number) {
  const mine = rem.bodies[mySide.value]
  if (mine.ultReady && !ultReadyPlayed) playUltReady()
  ultReadyPlayed = mine.ultReady
  const now = performance.now()
  if (now - lastHud < HUD_MS) return
  lastHud = now
  const a = rem.bodies.A
  const b = rem.bodies.B
  hud.value = {
    t,
    spin: { A: a.spin, B: b.spin },
    meter: { A: a.meter, B: b.meter },
    hits: { A: a.hits, B: b.hits },
    ult: { A: a.ult, B: b.ult },
    ultReady: mine.ultReady,
    dash: mine.dash,
  }
}

function updateHum(rem: RemoteArena) {
  if (!spinHum.running) return
  const radius = room.value ? arenaById(room.value.arena.id).radius : 4
  for (const side of ['A', 'B'] as const) {
    const b = rem.bodies[side]
    const speed = Math.min(1, Math.hypot(b.vx, b.vz) / 8)
    spinHum.update(side, b.spin, speed, (b.x / radius) * 0.8, !!cutIn.value)
  }
}

// ---------------------------------------------------------------- Ult-Einblendung

interface CutIn {
  id: number
  fighter: FighterDto | undefined
  ult: UltType
  mine: boolean
  durationMs: number
}
const cutIn = ref<CutIn | null>(null)
let cutInId = 0

/** Der Server hält den Kampf für die Einblendung kurz an (für beide gleich lang). */
function showCutIn(side: Side, ult: UltType) {
  const mine = side === mySide.value
  cutIn.value = {
    id: ++cutInId,
    fighter: mine ? me.value : them.value,
    ult,
    mine,
    durationMs: 900,
  }
  playUltCutIn(ult, mine)
}

function onCutInDone() {
  cutIn.value = null
}

const canUlt = computed(() => phase.value === 'fight' && !cutIn.value && hud.value.ultReady)

function activateUlt() {
  if (canUlt.value) ultQueued = true
}

// ---------------------------------------------------------------- Steuerung

const keys = new Set<string>()
let dashQueued = false
let ultQueued = false

/** Touch-Geräte: Bildschirm-Stick statt Tastatur */
const isTouch = ref(globalThis.matchMedia?.('(pointer: coarse)').matches ?? false)
const touchMove = { x: 0, z: 0 }
function onTouchMove(x: number, z: number) {
  touchMove.x = x
  touchMove.z = z
}
function onTouchDash() {
  dashQueued = true
}
function onPointerDown(e: PointerEvent) {
  if (e.pointerType === 'touch') isTouch.value = true
}

/** Kamera-Blickrichtung, die 3D-Szene schreibt sie jedes Frame */
const cameraView: CameraView = { yaw: 0 }
const onCameraYaw = (yaw: number) => {
  cameraView.yaw = yaw
}

/** Eingabe an den Server, relativ zur Kamera: W = ins Bild hinein, D = nach rechts. */
function sendInput() {
  if (!remote || phase.value !== 'fight') return
  let x = 0
  let z = 0
  if (keys.has('KeyW') || keys.has('ArrowUp')) z -= 1
  if (keys.has('KeyS') || keys.has('ArrowDown')) z += 1
  if (keys.has('KeyA') || keys.has('ArrowLeft')) x -= 1
  if (keys.has('KeyD') || keys.has('ArrowRight')) x += 1
  let len = Math.hypot(x, z) || 1
  if (x === 0 && z === 0) {
    // Touch-Stick: analog, Länge 0–1 bleibt erhalten
    x = touchMove.x
    z = touchMove.z
    len = 1
  }
  const dir = rotateInput(x / len, z / len, cameraView.yaw)
  remote.send(dir.x, dir.z, dashQueued, ultQueued)
  dashQueued = false
  ultQueued = false
}

const CONTROL_KEYS = new Set([
  'KeyW',
  'KeyA',
  'KeyS',
  'KeyD',
  'ArrowUp',
  'ArrowDown',
  'ArrowLeft',
  'ArrowRight',
  'ShiftLeft',
  'ShiftRight',
  'Space',
  'KeyQ',
])

function onKeyDown(e: KeyboardEvent) {
  if (phase.value !== 'fight') return
  const target = e.target as HTMLElement | null
  if (target?.closest('input, textarea, select, [contenteditable]')) return
  if (!CONTROL_KEYS.has(e.code)) return
  e.preventDefault()
  if (e.repeat) return
  keys.add(e.code)
  if (e.code === 'ShiftLeft' || e.code === 'ShiftRight') dashQueued = true
  if (e.code === 'Space' || e.code === 'KeyQ') activateUlt()
}

function onKeyUp(e: KeyboardEvent) {
  keys.delete(e.code)
}

function onBlur() {
  keys.clear()
}

window.addEventListener('keydown', onKeyDown)
window.addEventListener('keyup', onKeyUp)
window.addEventListener('blur', onBlur)
window.addEventListener('pointerdown', onPointerDown)
prepareSounds()

onUnmounted(() => {
  run++
  stopLive()
  window.removeEventListener('keydown', onKeyDown)
  window.removeEventListener('keyup', onKeyUp)
  window.removeEventListener('blur', onBlur)
  window.removeEventListener('pointerdown', onPointerDown)
})

// ---------------------------------------------------------------- Ergebnis

const outcome = computed(() => {
  const r = result.value
  if (!r) return null
  return {
    won: r.winner === mySide.value,
    finish: FINISH_LABEL[r.finish],
    durationMs: r.durationMs,
  }
})
const elapsedSec = computed(() => Math.round((outcome.value?.durationMs ?? 0) / 1000))

function again() {
  if (isCup.value) {
    router?.push({ name: 'turnier' })
    return
  }
  if (route.query.room) {
    // aus Lobby, Spielersuche oder Herausforderung: neuer Kampf gegen die KI
    router?.push({ name: 'arena' })
    return
  }
  void draw()
}

const againLabel = computed(() =>
  isCup.value ? 'Zurück zum Turnier' : pvp.value ? 'Gegen KI kämpfen' : 'Nochmal',
)

const inBattle = computed(() =>
  ['intro', 'launch', 'waiting', 'countdown', 'fight', 'result'].includes(phase.value),
)
const hudView = computed(() => (phase.value === 'intro' ? 'intro' : 'hud'))
const opponentLabel = computed(() => (pvp.value ? 'Gegner' : 'KI-Gegner'))

watch(() => route.fullPath, init, { immediate: true })
</script>

<template>
  <main class="arena-page arena-mode">
    <div class="stage" :class="{ 'no-touch-scroll': phase === 'fight' }">
      <BattleArena
        v-if="webgl && report && choreo && inBattle"
        :report="report"
        :choreo="choreo"
        :clock="clock"
        :calm="reducedMotion"
        :me="mySide"
        :camera-yaw="onCameraYaw"
      />
      <div v-else class="stage-fallback"><BeybladeSpinner /></div>

      <LaunchPad
        v-if="room && phase === 'launch'"
        :challenge="room.launch"
        :calm="reducedMotion"
        @charge="onCharge"
        @launch="onLaunch"
      />

      <div class="stage-overlay">
        <UltBadge
          v-if="(phase === 'fight' && !isTouch) || phase === 'result'"
          class="ult-badge-pos"
          :fighter="me"
          :value="hud.meter[mySide]"
          :active="hud.ult[mySide]"
          :interactive="canUlt"
          :calm="reducedMotion"
          @activate="activateUlt"
        />
        <UltCutIn
          v-if="cutIn"
          :key="cutIn.id"
          :fighter="cutIn.fighter"
          :ult="cutIn.ult"
          :mine="cutIn.mine"
          :power="null"
          :duration-ms="cutIn.durationMs"
          :calm="reducedMotion"
          @done="onCutInDone"
        />
        <button
          v-if="inBattle"
          type="button"
          class="sound-toggle mono"
          :aria-pressed="!soundMuted"
          :aria-label="soundMuted ? 'Ton einschalten' : 'Ton ausschalten'"
          @click="setSoundMuted(!soundMuted)"
        >
          {{ soundMuted ? 'Ton aus' : 'Ton an' }}
        </button>
        <p v-if="phase === 'fight' || phase === 'result'" class="clock mono" aria-live="off">
          {{ formatClock(hud.t) }}
        </p>
        <p v-if="countdown && phase === 'countdown'" :key="countdown" class="countdown">
          {{ countdown }}
        </p>
        <p v-if="phase === 'waiting'" class="stage-hint" aria-live="polite">
          Warte, bis {{ them?.username ?? 'dein Gegner' }} angespinnt hat …
        </p>
        <TouchControls
          v-if="phase === 'fight' && isTouch"
          :ult-ready="canUlt"
          :dash-charge="hud.dash"
          @move="onTouchMove"
          @dash="onTouchDash"
          @ult="activateUlt"
        />
        <div v-else-if="phase === 'fight'" class="controls mono" aria-hidden="true">
          <span><kbd>W</kbd><kbd>A</kbd><kbd>S</kbd><kbd>D</kbd> fahren</span>
          <span class="dash" :class="{ ready: hud.dash >= 1 }">
            <kbd>Shift</kbd> Dash
            <i class="dash-bar"><i :style="{ width: `${Math.max(0, hud.dash) * 100}%` }" /></i>
          </span>
          <span><kbd>Leertaste</kbd> Ult</span>
        </div>
      </div>
    </div>

    <div class="overlay">
      <section v-if="!inBattle" class="panel mine" aria-labelledby="arena-mode-title">
        <header class="panel-head">
          <span class="label"><i class="dot" />Arena-Modus</span>
          <span class="label">Echtzeit · eigener Rang</span>
        </header>
        <div class="panel-body">
          <h1 id="arena-mode-title" class="bey-name">Selbst steuern</h1>
          <p class="intro-text">
            Echte Physik statt fester Bahnen: Du fährst deinen Kreisel mit <kbd>W</kbd><kbd>A</kbd
            ><kbd>S</kbd><kbd>D</kbd>, rammst mit <kbd>Shift</kbd> und zündest die Ult mit
            <kbd>Leertaste</kbd>. Schub und Dash kosten Spin. Wer getroffen wird und durch eine
            Tasche rauscht, fliegt raus. Gegen Spieler geht es über Spielersuche, Lobby oder
            Freunde.
          </p>
          <p v-if="phase === 'loading'" class="hint">Lade …</p>
          <div v-else-if="beyblades.length === 0 && phase !== 'error'" class="hint">
            <p>Du hast noch keinen Kreisel gebaut.</p>
            <RouterLink to="/baukasten" class="btn-secondary">Jetzt bauen</RouterLink>
          </div>
          <div
            v-else-if="beyblades.length > 0"
            class="chips"
            role="radiogroup"
            aria-label="Kreisel wählen"
          >
            <button
              v-for="b in beyblades"
              :key="b.id"
              type="button"
              class="chip"
              role="radio"
              :aria-checked="b.id === selectedId"
              :class="{ active: b.id === selectedId }"
              :disabled="phase === 'creating'"
              @click="selectedId = b.id"
            >
              {{ b.name }}
            </button>
          </div>
          <p class="faint note">
            Eigener Rang, getrennt von der klassischen Rangliste. Gegen die KI geht es bis Gold,
            gegen Spieler unbegrenzt. Die Physik läuft auf dem Server, gewertet wird dort.
          </p>
        </div>
      </section>
      <FighterPanel
        v-else
        class="mine"
        label="Du"
        :fighter="me"
        :view="hudView"
        :energy="hud.spin[mySide]"
        :hits="hud.hits[mySide]"
        :winner="winnerFlag(mySide)"
      />

      <FighterPanel
        v-if="inBattle"
        class="theirs"
        :label="opponentLabel"
        :fighter="them"
        :view="hudView"
        :energy="hud.spin[theirSide]"
        :hits="hud.hits[theirSide]"
        :winner="winnerFlag(theirSide)"
        :ult="hud.meter[theirSide]"
        :ult-active="hud.ult[theirSide]"
      />

      <section v-if="phase === 'result' && outcome" class="panel result" aria-live="polite">
        <header class="panel-head">
          <span class="label"><i class="dot" />Ergebnis</span>
          <span class="label mono">{{ elapsedSec }} s</span>
        </header>
        <div class="panel-body">
          <h2 class="result-title" :class="outcome.won ? 'won' : 'lost'">
            {{ outcome.won ? 'Sieg' : 'Niederlage' }}
          </h2>
          <p class="faint">{{ outcome.finish }}</p>
          <div v-if="result?.you" class="rank-row">
            <LeagueBadge
              :tier="result.you.rank.tier"
              :division="result.you.rank.division"
              :size="44"
            />
            <div>
              <p class="lp mono">
                <template v-if="isCup">Cup-Kampf · keine LP</template>
                <template v-else>
                  <strong :class="result.you.ratingDelta >= 0 ? 'up' : 'down'"
                    >{{ result.you.ratingDelta >= 0 ? '+' : ''
                    }}{{ result.you.ratingDelta }} LP</strong
                  >
                  · {{ result.you.rank.rating }} LP
                </template>
              </p>
              <p class="faint">
                {{ result.you.rank.league }} · Arena-Rang · +{{ result.you.xpGained }} XP
              </p>
            </div>
          </div>
        </div>
      </section>

      <div class="cta-wrap">
        <template v-if="phase === 'select' || phase === 'creating' || phase === 'loading'">
          <button
            type="button"
            class="cta"
            :disabled="!selected || phase !== 'select'"
            @click="draw"
          >
            <span>{{ phase === 'creating' ? 'Suche Gegner …' : 'Gegen KI kämpfen' }}</span>
            <span class="cta-arrow" aria-hidden="true">→</span>
          </button>
          <p class="cta-sub" :role="error ? 'alert' : undefined">
            {{ error ?? 'Zufälliger KI-Gegner · zufällige Arena · Tastatur oder Touch' }}
          </p>
          <RouterLink to="/lobby" class="cta-sub mode-link"
            >Gegen Spieler: Spielersuche →</RouterLink
          >
        </template>

        <template v-else-if="phase === 'intro'">
          <button type="button" class="cta" @click="phase = 'launch'">
            <span>Anspinnen</span>
            <span class="cta-arrow" aria-hidden="true">→</span>
          </button>
          <p class="cta-sub">
            {{ me ? ARCHETYPE_LABEL[me.archetype] : '' }} vs
            {{ them ? ARCHETYPE_LABEL[them.archetype] : '' }} · {{ room?.arena.name }}
          </p>
        </template>

        <template v-else-if="phase === 'launch'">
          <p class="cta-sub launch-sub">Guter Start = mehr Spin und Schwung</p>
        </template>

        <template v-else-if="phase === 'result'">
          <div class="result-actions">
            <RouterLink v-if="!isCup" to="/rangliste?modus=arena" class="btn-secondary"
              >Arena-Rangliste</RouterLink
            >
            <button type="button" class="cta" @click="again">
              <span>{{ againLabel }}</span>
              <span class="cta-arrow" aria-hidden="true">→</span>
            </button>
          </div>
        </template>

        <template v-else-if="phase === 'error'">
          <button type="button" class="cta" @click="init">
            <span>Nochmal versuchen</span>
            <span class="cta-arrow" aria-hidden="true">↻</span>
          </button>
          <p class="cta-sub" role="alert">{{ error }}</p>
        </template>
      </div>
    </div>
  </main>
</template>

<style scoped>
.mine {
  grid-column: 1;
}

.no-touch-scroll {
  touch-action: none;
}

.theirs {
  grid-column: 3;
}

.result {
  grid-column: 2;
  align-self: center;
  justify-self: center;
  width: min(340px, 100%);
}

.bey-name {
  margin: 0 0 14px;
  font-size: 2.2rem;
  font-weight: 600;
  line-height: 1.05;
  letter-spacing: -0.035em;
}

.intro-text {
  margin: 0 0 18px;
  color: var(--text-muted);
  line-height: 1.55;
}

.note {
  margin: 16px 0 0;
  font-size: 0.8rem;
}

kbd {
  display: inline-block;
  min-width: 1.4em;
  margin: 0 2px;
  padding: 1px 5px;
  border: 1px solid var(--line-strong);
  border-radius: 2px;
  font-family: var(--font-mono);
  font-size: 0.75em;
  text-align: center;
  color: var(--text);
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.chip {
  padding: 6px 10px;
  border: 1px solid var(--line-strong);
  border-radius: 3px;
  background: none;
  font-size: 0.82rem;
  color: var(--text-muted);
  cursor: pointer;
}

.chip.active {
  border-color: var(--text);
  color: var(--text);
}

.stage-overlay {
  pointer-events: none;
}

.sound-toggle {
  position: absolute;
  top: 24px;
  left: calc(50% + 48px);
  z-index: 3;
  padding: 4px 10px;
  border: 1px solid var(--line);
  border-radius: 3px;
  background: var(--bg);
  color: var(--text-muted);
  font-size: 0.75rem;
  cursor: pointer;
  pointer-events: auto;
}

.sound-toggle[aria-pressed='false'] {
  text-decoration: line-through;
}

.ult-badge-pos {
  position: absolute;
  bottom: 24px;
  left: 24px;
  z-index: 3;
}

.clock {
  position: absolute;
  top: 24px;
  left: 50%;
  margin: 0;
  padding: 4px 10px;
  border: 1px solid var(--line);
  border-radius: 3px;
  background: var(--bg);
  font-size: 0.95rem;
  transform: translateX(-50%);
}

.controls {
  position: absolute;
  bottom: 24px;
  left: 50%;
  display: flex;
  gap: 18px;
  padding: 6px 12px;
  border-radius: 3px;
  background: var(--bg);
  font-size: 0.72rem;
  color: var(--text-muted);
  transform: translateX(-50%);
  white-space: nowrap;
}

.dash {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.dash-bar {
  position: relative;
  display: inline-block;
  width: 36px;
  height: 4px;
  margin-left: 4px;
  border-radius: 2px;
  background: var(--line);
  overflow: hidden;
}

.dash-bar > i {
  position: absolute;
  inset: 0 auto 0 0;
  background: var(--text-muted);
}

.dash.ready .dash-bar > i {
  background: var(--accent, #f5c84c);
}

.countdown {
  position: absolute;
  top: 50%;
  left: 50%;
  margin: 0;
  font-size: clamp(4rem, 12vw, 9rem);
  font-weight: 600;
  letter-spacing: -0.05em;
  text-shadow: 0 8px 40px rgba(0, 0, 0, 0.8);
  transform: translate(-50%, -50%);
  animation: count 0.6s cubic-bezier(0.2, 0.8, 0.2, 1);
}

@keyframes count {
  from {
    opacity: 0;
    transform: translate(-50%, -50%) scale(1.6);
  }
}

.stage-hint {
  position: absolute;
  top: 24px;
  left: 50%;
  margin: 0;
  padding: 4px 10px;
  border-radius: 3px;
  background: var(--bg);
  font-family: var(--font-mono);
  font-size: 0.72rem;
  color: var(--text-muted);
  transform: translateX(-50%);
  white-space: nowrap;
}

.mode-link {
  padding: 4px 10px;
  border-radius: 3px;
  background: var(--bg);
  text-decoration: none;
}

.launch-sub {
  padding: 4px 10px;
  border-radius: 3px;
  background: var(--bg);
}

.result-title {
  margin: 0 0 4px;
  font-size: 2rem;
  letter-spacing: -0.03em;
}

.result-title.won {
  color: var(--good, #6fd08c);
}

.result-title.lost {
  color: var(--bad, #ef6a5b);
}

.rank-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 16px;
}

.rank-row p {
  margin: 0;
}

.lp .up {
  color: var(--good, #6fd08c);
}

.lp .down {
  color: var(--bad, #ef6a5b);
}

.result-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
}

.result-actions .btn-secondary {
  background: var(--bg);
}

@media (max-width: 960px) {
  .result {
    grid-column: 1 / -1;
  }

  .controls {
    display: none;
  }
}
</style>
