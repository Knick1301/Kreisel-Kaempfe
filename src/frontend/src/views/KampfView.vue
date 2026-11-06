<script setup lang="ts">
import { computed, defineAsyncComponent, markRaw, onUnmounted, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ARCHETYPE_LABEL,
  createBotMatch,
  fetchBattle,
  fetchBeyblades,
  fetchMatch,
  finishMatch,
  submitLaunch,
  triggerUlt,
  type BattleReport,
  type BeybladeDto,
  type FighterDto,
  type LaunchInput,
  type MatchDto,
  type Side,
  type UltType,
} from '@/api'
import { auth } from '@/auth'
import { refreshMe } from '@/stores/me'
import { pickActive, setActiveId } from '@/activeBeyblade'
import { hasWebGL } from '@/webgl'
import { ARENA_LIST, arenaById, arenaVariant } from '@/game/arenas'
import { createChoreography, type Choreography } from '@/game/choreographer'
import { SIGNAL } from '@/game/launch'
import { createStandbyChoreography, type StandbyChoreography } from '@/game/standby'
import {
  activeUlt,
  displayEnergy,
  fighterOf,
  hitGhost,
  formatClock,
  isDeflected,
  matchupLabel,
  mySideOf,
  other,
  ultAt,
  ultWindows,
} from '@/game/playback'
import { ULT_COLOR } from '@/game/ult'
import {
  playCountdown,
  playUltCutIn,
  playUltReady,
  prepareSounds,
  setSoundMuted,
  soundMuted,
} from '@/game/sound'
import { useBattlePlayback } from '@/composables/useBattlePlayback'
import { useModelUrls } from '@/composables/useModelUrls'
import BattleResultDialog from '@/components/BattleResultDialog.vue'
import BeybladeSpinner from '@/components/BeybladeSpinner.vue'
import StatBar from '@/components/StatBar.vue'
import BattleLog from '@/components/battle/BattleLog.vue'
import FighterPanel from '@/components/battle/FighterPanel.vue'
import UltBadge from '@/components/battle/UltBadge.vue'
import UltCutIn from '@/components/battle/UltCutIn.vue'
import ArenaPicker from '@/components/battle/ArenaPicker.vue'
import LaunchPad from '@/components/battle/LaunchPad.vue'

const ArenaStage = defineAsyncComponent(() => import('@/components/three/ArenaStage.vue'))
const BattleArena = defineAsyncComponent(() => import('@/components/three/battle/BattleArena.vue'))

type Phase =
  | 'loading'
  | 'select'
  | 'drawing'
  | 'intro'
  | 'launch'
  | 'resolving'
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
const report = shallowRef<BattleReport | null>(null)
const choreo = shallowRef<Choreography | null>(null)
/** Laufendes Match vor dem Kampf (Anspinnen), null bei Replays */
const match = shallowRef<MatchDto | null>(null)
let standby: StandbyChoreography | null = null
const mySide = ref<Side>('A')
const rolling = ref<string | null>(null)
const countdown = ref<string | null>(null)
const showDialog = ref(false)
const calm = ref(false)
const spinKey = ref(0)

// Zuletzt gewählte Arena und je Arena die Variante merken, damit „Nochmal“ direkt dort startet
const ARENA_KEY = 'kreisel-kaempfer.arena'
interface ArenaChoice {
  arena: string
  variants: Record<string, string>
}
function loadArena(): ArenaChoice {
  const fallback: ArenaChoice = { arena: ARENA_LIST[0]!.id, variants: {} }
  try {
    const raw = globalThis.localStorage?.getItem?.(ARENA_KEY)
    if (!raw) return fallback
    // früher stand hier nur die ID, der alte Krater ist jetzt die Vulkan-Variante des Standard-Stadions
    const parsed: ArenaChoice = raw.startsWith('{') ? JSON.parse(raw) : { arena: raw, variants: {} }
    const arena = arenaById(parsed.arena)
    if (arena.hidden) return { arena: 'standard', variants: { standard: 'vulkan' } }
    return { arena: arena.id, variants: parsed.variants ?? {} }
  } catch {
    return fallback
  }
}
const arenaChoice = ref<ArenaChoice>(loadArena())
const arenaId = computed(() => arenaChoice.value.arena)
function saveArena() {
  try {
    globalThis.localStorage?.setItem?.(ARENA_KEY, JSON.stringify(arenaChoice.value))
  } catch {
    return
  }
}
function chooseArena(id: string) {
  arenaChoice.value = { ...arenaChoice.value, arena: id }
  saveArena()
}
function chooseVariant(id: string) {
  const c = arenaChoice.value
  arenaChoice.value = { ...c, variants: { ...c.variants, [c.arena]: id } }
  saveArena()
}
const chosenArena = computed(() => arenaById(arenaId.value))
const chosenVariant = computed(() =>
  arenaVariant(chosenArena.value, arenaChoice.value.variants[arenaId.value]),
)
const arenaLabel = computed(() => `${chosenArena.value.name} · ${chosenVariant.value.name}`)
const variantOf = (id: string) => arenaVariant(arenaById(id), arenaChoice.value.variants[id])

// Vorschau vor dem Kampf: gewählte Arena und Look, der eigene Kreisel wartet im Launcher
const previewKey = computed(() => `${arenaId.value}:${chosenVariant.value.id}`)
const previewChoreo = computed(() => markRaw(createStandbyChoreography(arenaId.value)))
const previewReport = computed<BattleReport | null>(() => {
  const b = selected.value
  if (!b) return null
  const fighter: FighterDto = {
    side: 'A',
    username: auth.username,
    bot: false,
    beyblade: b.name,
    stats: b.stats,
    style: { attack: 1 / 3, defense: 1 / 3, stamina: 1 / 3 },
    archetype: 'BALANCE',
    parts: [b.fusionWheel, b.spinTrack, b.tip],
  }
  return {
    id: 0,
    winner: '',
    loser: '',
    winnerBeyblade: '',
    loserBeyblade: '',
    finish: 'DECISION',
    rounds: 0,
    playedAt: '',
    arena: { id: arenaId.value, name: chosenArena.value.name, variant: chosenVariant.value.id },
    fighters: [fighter, { ...fighter, side: 'B' }],
  }
})

const reducedMotion = globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
const webgl = hasWebGL()
const playback = useBattlePlayback()
const { time, callouts } = playback

const replayId = computed(() => (route.params.id ? Number(route.params.id) : null))
const isReplay = computed(() => replayId.value !== null)

// ---------------------------------------------------------------- abgeleitete Anzeige

const me = computed(() => (report.value ? fighterOf(report.value, mySide.value) : undefined))
const them = computed(() =>
  report.value ? fighterOf(report.value, other(mySide.value)) : undefined,
)
const names = computed<Record<Side, string>>(() => ({
  A: report.value?.fighters?.[0].beyblade ?? 'A',
  B: report.value?.fighters?.[1].beyblade ?? 'B',
}))
const animate = computed(() => webgl && !!choreo.value && (!reducedMotion || calm.value))
// In 3D stehen Treffer an der Trefferstelle, oben dann nur noch große Momente (Ult, Finish)
watch(animate, (on) => playback.setQuietHits(on), { immediate: true })
const inBattle = computed(() =>
  ['intro', 'launch', 'resolving', 'countdown', 'fight', 'result'].includes(phase.value),
)
// Beim Anspinnen schmale HUD-Panels, damit die Launcher am Rand frei bleiben
const hudView = computed(() =>
  ['launch', 'resolving', 'fight', 'result'].includes(phase.value) ? 'hud' : 'intro',
)
const finished = computed(() => phase.value === 'result')

function energy(side: Side) {
  // Vor dem Kampf (Match ohne Timeline) volle Energie zeigen
  if (!report.value?.timeline) return 1
  return displayEnergy(report.value, side, Math.max(0, time.value))
}

/** roter Rest in der Spin-Leiste nach Treffern, nur bis zum Finish */
function ghost(side: Side) {
  const tl = report.value?.timeline
  if (!tl || time.value < 0 || time.value > (report.value?.durationMs ?? Infinity)) return null
  return hitGhost(tl, side, time.value)
}

const ultTimeline = computed(() => ultWindows(report.value?.timeline?.events ?? []))

/** Ult-Leiste fürs HUD, null ohne Ults (Altbestand) oder vor dem Kampf */
function ultMeter(side: Side) {
  const tl = report.value?.timeline
  if (!tl) return null
  const finishMs = report.value?.durationMs ?? Infinity
  return ultAt(tl, side, Math.min(Math.max(0, time.value), finishMs))
}

function ultRunning(side: Side) {
  if (time.value > (report.value?.durationMs ?? Infinity)) return null
  return activeUlt(ultTimeline.value, side, time.value)?.ult ?? null
}

function hitsUntil(side: Side) {
  const events = report.value?.timeline?.events ?? []
  let n = 0
  events.forEach((e, i) => {
    if (e.type === 'CLASH' && e.t <= time.value && e.attacker === side && !isDeflected(events, i))
      n++
  })
  return n
}

const matchupText = computed(() => {
  if (!me.value || !them.value) return ''
  return `${ARCHETYPE_LABEL[me.value.archetype]} vs ${ARCHETYPE_LABEL[them.value.archetype]} · ${matchupLabel(report.value?.matchup, mySide.value)}`
})

const winnerFlag = (side: Side) =>
  finished.value && report.value?.winnerSide ? report.value.winnerSide === side : null

const selectedParts = computed(() =>
  selected.value ? [selected.value.fusionWheel, selected.value.spinTrack, selected.value.tip] : [],
)
const showcaseUrls = useModelUrls(() => selectedParts.value)
const selectedStats = computed(() => {
  const s = selected.value?.stats
  if (!s) return []
  return [
    { label: 'Angriff', value: s.attack },
    { label: 'Verteidigung', value: s.defense },
    { label: 'Ausdauer', value: s.stamina },
  ]
})

// ---------------------------------------------------------------- Ablauf

const wait = (ms: number) => new Promise((r) => setTimeout(r, ms))
let run = 0

async function init() {
  if (route.name !== 'kampf' && route.name !== 'kampf-replay') return
  run++
  playback.reset()
  report.value = null
  choreo.value = null
  match.value = null
  showDialog.value = false
  error.value = null
  calm.value = false
  phase.value = 'loading'
  if (replayId.value !== null) return loadReplay(replayId.value)
  try {
    const list = await fetchBeyblades()
    beyblades.value = Array.isArray(list) ? list : []
    const wanted = Number(route.query.bey)
    const pre = beyblades.value.find((b) => b.id === wanted) ?? pickActive(beyblades.value)
    // `?bey=` wählt nur den Kreisel vor, gezogen wird erst nach der Arena-Wahl
    selectedId.value = pre?.id ?? null
    phase.value = 'select'
  } catch (e) {
    fail(e)
  }
}

async function loadReplay(id: number) {
  try {
    const r = await fetchBattle(id)
    prepare(r)
    if (r.timeline) {
      phase.value = 'intro'
    } else {
      phase.value = 'result'
      showDialog.value = true
    }
  } catch (e) {
    fail(e)
  }
}

function fail(e: unknown) {
  error.value = e instanceof Error ? e.message : String(e)
  phase.value = 'error'
}

// ---------------------------------------------------------------- Ult selbst auslösen

/** Laufendes Match, in dem der Spieler seine Ult noch auslösen kann; null bei Replays */
const liveMatchId = ref<string | null>(null)
/** Druck läuft (Anfrage beim Server) */
const pressing = ref(false)
/** Kampfzeit des letzten eigenen Drucks: Das ULT-Ereignis dazu hat seine Einblendung schon bekommen */
let pressedAt = -Infinity

interface CutIn {
  id: number
  fighter: FighterDto | undefined
  ult: UltType
  mine: boolean
  power: number | null
  durationMs: number
}
const cutIn = ref<CutIn | null>(null)
let cutInId = 0
let cutInDone: (() => void) | null = null

const canUlt = computed(
  () =>
    !!liveMatchId.value &&
    phase.value === 'fight' &&
    !pressing.value &&
    !cutIn.value &&
    (ultMeter(mySide.value) ?? 0) >= 1 &&
    !ultRunning(mySide.value) &&
    time.value < (report.value?.durationMs ?? 0),
)

/** Einblendung zeigen, Kampf anhalten; `onDone` läuft, wenn sie vorbei ist. */
function showCutIn(c: Omit<CutIn, 'id' | 'durationMs'>, onDone?: () => void) {
  const quiet = calm.value || reducedMotion
  cutIn.value = {
    ...c,
    id: ++cutInId,
    durationMs: c.mine ? (quiet ? 900 : 1500) : quiet ? 700 : 1150,
  }
  cutInDone = onDone ?? null
  playback.hold()
  playUltCutIn(c.ult, c.mine)
}

function onCutInDone() {
  const done = cutInDone
  cutIn.value = null
  cutInDone = null
  if (done) done()
  else playback.resume()
}

/** Ult auslösen: Uhr anhalten, Einblendung zeigen, Server rechnet ab diesem Moment neu. */
async function activateUlt() {
  const id = liveMatchId.value
  if (!canUlt.value || !id) return
  const ult = me.value?.ult?.type
  if (!ult) return
  const t = playback.clock.t
  const token = run
  pressing.value = true
  let response: BattleReport | null = null
  let failed = false
  let shown = false
  const finish = () => {
    if (!shown || (!response && !failed)) return
    pressing.value = false
    if (response) applyNewTimeline(response)
    playback.resume()
  }
  showCutIn({ fighter: me.value, ult, mine: true, power: null }, () => {
    shown = true
    finish()
  })
  try {
    const m = await triggerUlt(id, t)
    if (token !== run) return
    response = m.report ?? null
    if (!response) failed = true
    pressedAt = t
    const fired = response?.timeline?.events.find(
      (e) => e.type === 'ULT' && e.side === mySide.value && e.t >= t,
    )
    if (fired?.type === 'ULT' && cutIn.value)
      cutIn.value = { ...cutIn.value, power: fired.intensity }
  } catch {
    failed = true
  }
  finish()
}

/** Neuer Kampfverlauf nach dem Druck: Bis jetzt identisch, ab jetzt mit Ult. */
function applyNewTimeline(r: BattleReport) {
  report.value = r
  choreo.value = r.timeline ? markRaw(createChoreography(r)) : null
  playback.replace(r, choreo.value)
}

playback.onEvent((e) => {
  if (e.type === 'ULT_READY') {
    if (e.side === mySide.value && liveMatchId.value) playUltReady()
    return true
  }
  if (e.type !== 'ULT') return false
  // Eigene, selbst ausgelöste Ult: Einblendung lief schon beim Druck
  if (e.side === mySide.value && e.t >= pressedAt && e.t < pressedAt + 100) return true
  const fighter = report.value ? fighterOf(report.value, e.side) : undefined
  showCutIn({ fighter, ult: e.ult, mine: e.side === mySide.value, power: e.intensity })
  return true
})

function onUltKey(e: KeyboardEvent) {
  if (e.repeat || !canUlt.value) return
  const target = e.target as HTMLElement | null
  if (target?.closest('input, textarea, select, [contenteditable]')) return
  if (e.code === 'Space' || e.key === ' ' || e.key === 'q' || e.key === 'Q') {
    e.preventDefault()
    void activateUlt()
  }
}
window.addEventListener('keydown', onUltKey)
// Metallklänge im Leerlauf vorrechnen, solange man noch Kreisel und Arena wählt
prepareSounds()
onUnmounted(() => window.removeEventListener('keydown', onUltKey))

function prepare(r: BattleReport) {
  report.value = r
  mySide.value = mySideOf(r, auth.username)
  choreo.value = r.timeline ? markRaw(createChoreography(r)) : null
  playback.load(r, choreo.value, mySide.value, calm.value || reducedMotion, onFightEnd)
}

// Nur Optik: rotierende Namen, während der Server den Gegner zieht.
const ROULETTE_A = ['Eisiger', 'Stählerner', 'Rasender', 'Dunkler', 'Goldener', 'Tosender']
const ROULETTE_B = ['Orkan', 'Greif', 'Komet', 'Phönix', 'Taifun', 'Wächter']
let roulette: ReturnType<typeof setInterval> | undefined
function startRoulette() {
  let i = 0
  roulette = setInterval(() => {
    i++
    rolling.value = `${ROULETTE_A[i % ROULETTE_A.length]} ${ROULETTE_B[(i * 5) % ROULETTE_B.length]}`
  }, 80)
  rolling.value = 'Ziehe Gegner …'
}
function stopRoulette() {
  clearInterval(roulette)
  rolling.value = null
}

async function draw() {
  const bey = selected.value
  if (!bey || phase.value === 'drawing') return
  const token = ++run
  setActiveId(bey.id)
  playback.reset()
  showDialog.value = false
  error.value = null
  calm.value = false
  report.value = null
  choreo.value = null
  match.value = null
  phase.value = 'drawing'
  startRoulette()
  try {
    const [m] = await Promise.all([
      createBotMatch(bey.id, arenaId.value, chosenVariant.value.id),
      wait(reducedMotion ? 200 : 1200),
    ])
    if (token !== run) return
    showMatch(m)
    phase.value = 'intro'
  } catch (e) {
    if (token !== run) return
    error.value = e instanceof Error ? e.message : String(e)
    phase.value = 'select'
  } finally {
    stopRoulette()
  }
}

/**
 * Vor dem Anspinnen: Kämpfer und Arena aus dem Match zeigen, die Kreisel warten im Launcher. Ein Ergebnis
 * gibt es noch nicht, der Kampf wird erst simuliert, wenn beide angespinnt haben.
 */
function showMatch(m: MatchDto) {
  match.value = m
  mySide.value = m.yourSide
  playback.reset()
  report.value = {
    id: 0,
    winner: '',
    loser: '',
    winnerBeyblade: '',
    loserBeyblade: '',
    finish: 'DECISION',
    rounds: 0,
    playedAt: '',
    mode: m.mode,
    arena: m.arena,
    fighters: m.fighters,
    matchup: m.matchup,
  }
  standby = markRaw(createStandbyChoreography(m.arena.id))
  choreo.value = standby
}

function begin() {
  if (match.value) {
    phase.value = 'launch'
    return
  }
  if (!report.value?.timeline || (reducedMotion && !calm.value)) {
    playback.skip()
    return
  }
  void runCountdown()
}

function onCharge(charge: number) {
  standby?.setCharge(mySide.value, charge)
}

/** Nach dem Urteil kurz stehen lassen, dann startet der Kampf direkt (das Anspinnen war der Countdown). */
const VERDICT_MS = 900

async function onLaunch(input: LaunchInput) {
  const m = match.value
  if (!m) return
  const token = ++run
  phase.value = 'resolving'
  try {
    const [done] = await Promise.all([awaitReport(m.id, input, token), wait(VERDICT_MS)])
    if (token !== run || !done) return
    // Der Kampf ist erst vorläufig: Bis zum Ende kann der Spieler seine Ult auslösen
    liveMatchId.value = done.id ? null : m.id
    pressedAt = -Infinity
    match.value = null
    standby = null
    prepare(done)
    if (!done.timeline || (reducedMotion && !calm.value)) {
      playback.skip()
      return
    }
    phase.value = 'fight'
    playback.start()
  } catch (e) {
    if (token !== run) return
    fail(e)
  }
}

/**
 * Start abgeben und auf den Kampf warten. Gegen die KI kommt der Bericht sofort zurück, gegen einen Menschen
 * (später) fragt der Client nach, bis auch der Gegner angespinnt hat.
 */
async function awaitReport(id: string, input: LaunchInput, token: number) {
  let m = await submitLaunch(id, input)
  while (!m.report && token === run) {
    await wait(500)
    m = await fetchMatch(id)
  }
  return m.report ?? null
}

async function runCountdown() {
  const token = ++run
  showDialog.value = false
  playback.reset()
  phase.value = 'countdown'
  const steps = calm.value || reducedMotion ? [SIGNAL] : ['3', '2', '1', SIGNAL]
  for (const step of steps) {
    countdown.value = step
    playCountdown(step === SIGNAL)
    await wait(step === SIGNAL ? 450 : 650)
    if (token !== run) return
  }
  countdown.value = null
  phase.value = 'fight'
  playback.start()
}

function onFightEnd() {
  cutIn.value = null
  cutInDone = null
  pressing.value = false
  const id = liveMatchId.value
  liveMatchId.value = null
  if (!id) {
    showResult()
    return
  }
  // Jetzt erst werten lassen: Ergebnis mit Liga-Punkten kommt vom Server
  const token = run
  phase.value = 'result'
  finishMatch(id)
    .then((m) => {
      if (token !== run) return
      if (m.report) report.value = m.report
      showResult()
    })
    .catch((e) => {
      if (token === run) fail(e)
    })
}

function showResult() {
  // Erst jetzt neu laden, damit Header und Dialog das Ergebnis nicht vorher verraten
  void refreshMe()
  phase.value = 'result'
  showDialog.value = true
}

function skip() {
  playback.skip()
}

function replay() {
  showDialog.value = false
  void runCountdown()
}

/** Bei reduzierter Bewegung: die ruhige Version doch abspielen. */
function watchCalm() {
  if (!report.value) return
  calm.value = true
  playback.load(report.value, choreo.value, mySide.value, true, onFightEnd)
  replay()
}

function again() {
  if (isReplay.value) {
    router.push({ name: 'kampf' })
    return
  }
  void draw()
}

function select(id: number) {
  selectedId.value = id
  spinKey.value++
}

watch(() => route.fullPath, init, { immediate: true })
onUnmounted(() => {
  run++
  stopRoulette()
})

const launchChallenge = computed(() => match.value?.launch ?? null)
const introLabel = computed(() => {
  if (match.value) return 'Anspinnen'
  return reducedMotion && !calm.value ? 'Ergebnis ansehen' : 'Kampf starten'
})

const dialogNote = computed(() =>
  report.value && !report.value.timeline
    ? 'Für diesen Kampf gibt es keine Wiedergabe (älterer Kampf oder geänderte Simulation).'
    : null,
)
</script>

<template>
  <main class="arena-page kampf">
    <div class="stage" @click="phase === 'select' && spinKey++">
      <BattleArena
        v-if="animate && report && choreo && inBattle"
        :report="report"
        :choreo="choreo"
        :clock="playback.clock"
        :calm="calm || reducedMotion"
        :me="mySide"
      />
      <BattleLog v-else-if="report && inBattle" :report="report" :time="time" :names="names" />
      <!-- Vorschau der gewählten Arena mit dem eigenen Kreisel im Launcher -->
      <BattleArena
        v-else-if="webgl && previewReport && previewChoreo && !inBattle"
        :key="previewKey"
        :report="previewReport"
        :choreo="previewChoreo"
        :clock="playback.clock"
        :calm="true"
        solo
      />
      <ArenaStage
        v-else-if="webgl && selected"
        :urls="phase === 'loading' ? undefined : showcaseUrls"
        :spin-key="spinKey"
        :parts="selectedParts"
      />
      <div v-else class="stage-fallback"><BeybladeSpinner /></div>

      <LaunchPad
        v-if="launchChallenge && (phase === 'launch' || phase === 'resolving')"
        :challenge="launchChallenge"
        :calm="calm || reducedMotion"
        @charge="onCharge"
        @launch="onLaunch"
      />

      <div class="stage-overlay">
        <UltBadge
          v-if="(phase === 'fight' || phase === 'result') && ultMeter(mySide) !== null"
          class="ult-badge-pos"
          :fighter="me"
          :value="ultMeter(mySide) ?? 0"
          :active="ultRunning(mySide)"
          :interactive="!!liveMatchId && phase === 'fight' && !pressing && !cutIn"
          :calm="calm || reducedMotion"
          @activate="activateUlt"
        />
        <UltCutIn
          v-if="cutIn"
          :key="cutIn.id"
          :fighter="cutIn.fighter"
          :ult="cutIn.ult"
          :mine="cutIn.mine"
          :power="cutIn.power"
          :duration-ms="cutIn.durationMs"
          :calm="calm || reducedMotion"
          @done="onCutInDone"
        />
        <button
          v-if="inBattle"
          type="button"
          class="sound-toggle mono"
          :aria-pressed="!soundMuted"
          :aria-label="soundMuted ? 'Ton einschalten' : 'Ton ausschalten'"
          :title="soundMuted ? 'Ton einschalten' : 'Ton ausschalten'"
          @click="setSoundMuted(!soundMuted)"
        >
          {{ soundMuted ? 'Ton aus' : 'Ton an' }}
        </button>
        <p v-if="phase === 'fight' || phase === 'result'" class="clock mono" aria-live="off">
          {{ formatClock(time) }}
        </p>
        <TransitionGroup tag="ul" name="pop" class="callouts" aria-live="polite">
          <li
            v-for="c in callouts"
            :key="c.id"
            :class="[c.tone, { ult: !!c.ult }]"
            :style="c.ult ? { '--ult': ULT_COLOR[c.ult] } : undefined"
          >
            {{ c.text }}
          </li>
        </TransitionGroup>
        <p v-if="countdown" :key="countdown" class="countdown" aria-live="assertive">
          {{ countdown }}
        </p>
        <p v-if="report && inBattle && !animate && phase === 'intro'" class="stage-hint">
          {{
            webgl
              ? 'Ruhige Darstellung: Der Kampf läuft als Text ab.'
              : 'Ohne WebGL läuft der Kampf als Text ab.'
          }}
        </p>
      </div>
    </div>

    <div class="overlay">
      <!-- Eigener Kreisel -->
      <section v-if="!inBattle" class="panel mine" aria-labelledby="mine-name">
        <header class="panel-head">
          <span class="label"><i class="dot" />Dein Kreisel</span>
          <span v-if="selected" class="label">#{{ String(selected.id).padStart(3, '0') }}</span>
        </header>
        <div class="panel-body">
          <p v-if="phase === 'loading'" class="hint">Lade Kreisel …</p>
          <div v-else-if="beyblades.length === 0 && phase !== 'error'" class="hint">
            <p>Du hast noch keinen Kreisel gebaut.</p>
            <RouterLink to="/baukasten" class="btn-secondary">Jetzt bauen</RouterLink>
          </div>
          <template v-else-if="selected">
            <h1 id="mine-name" class="bey-name">{{ selected.name }}</h1>
            <div
              v-if="beyblades.length > 1"
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
                :disabled="phase === 'drawing'"
                @click="select(b.id)"
              >
                {{ b.name }}
              </button>
            </div>
            <div class="stats">
              <StatBar
                v-for="s in selectedStats"
                :key="s.label"
                :label="s.label"
                :value="s.value"
              />
            </div>
          </template>
        </div>
      </section>
      <FighterPanel
        v-else
        class="mine"
        label="Du"
        :fighter="me"
        :view="hudView"
        :energy="energy(mySide)"
        :hits="hitsUntil(mySide)"
        :winner="winnerFlag(mySide)"
        :ghost="ghost(mySide)"
      />

      <!-- Gegner -->
      <FighterPanel
        v-if="inBattle || phase === 'drawing'"
        class="theirs"
        :label="them?.bot ? 'KI-Gegner' : 'Gegner'"
        :fighter="them"
        :view="hudView"
        :energy="energy(other(mySide))"
        :hits="hitsUntil(other(mySide))"
        :winner="winnerFlag(other(mySide))"
        :rolling="rolling"
        :ghost="ghost(other(mySide))"
        :ult="ultMeter(other(mySide))"
        :ult-active="ultRunning(other(mySide))"
      />
      <ArenaPicker
        v-else
        class="theirs"
        :arena-id="arenaId"
        :variants="arenaChoice.variants"
        @arena="chooseArena"
        @variant="chooseVariant"
      />

      <div class="cta-wrap">
        <template v-if="phase === 'select' || phase === 'drawing' || phase === 'loading'">
          <button
            type="button"
            class="cta"
            :disabled="!selected || phase !== 'select'"
            @click="draw"
          >
            <span>{{ phase === 'drawing' ? 'Ziehe Gegner …' : 'Gegner ziehen' }}</span>
            <span class="cta-arrow" aria-hidden="true">→</span>
          </button>
          <p class="cta-sub" :role="error ? 'alert' : undefined">
            {{ error ?? `Zufälliger KI-Gegner · ${arenaLabel}` }}
          </p>
        </template>

        <template v-else-if="phase === 'intro'">
          <button type="button" class="cta" @click="begin">
            <span>{{ introLabel }}</span>
            <span class="cta-arrow" aria-hidden="true">→</span>
          </button>
          <p class="cta-sub">{{ matchupText }}</p>
        </template>

        <template v-else-if="phase === 'launch' || phase === 'resolving'">
          <p class="cta-sub launch-sub">
            {{ phase === 'resolving' ? 'Kampf wird ausgewertet …' : arenaLabel }}
          </p>
        </template>

        <template v-else-if="phase === 'countdown' || phase === 'fight'">
          <button type="button" class="btn-secondary skip" @click="skip">
            Überspringen <span aria-hidden="true">⏭</span>
          </button>
          <p class="cta-sub">{{ report?.arena?.name ?? 'Standard-Stadion' }}</p>
        </template>

        <template v-else-if="phase === 'result'">
          <div
            v-if="!isReplay"
            class="next-arena"
            role="radiogroup"
            aria-label="Arena für den nächsten Kampf"
          >
            <button
              v-for="a in ARENA_LIST"
              :key="a.id"
              type="button"
              class="chip"
              role="radio"
              :aria-checked="a.id === arenaId"
              :class="{ active: a.id === arenaId }"
              @click="chooseArena(a.id)"
            >
              <i class="arena-dot" :style="{ background: variantOf(a.id).palette.accent }" />{{
                a.name
              }}
            </button>
          </div>
          <div class="result-actions">
            <button v-if="report?.timeline" type="button" class="btn-secondary" @click="replay">
              Replay
            </button>
            <button type="button" class="btn-secondary" @click="showDialog = true">Ergebnis</button>
            <button type="button" class="cta" @click="again">
              <span>{{ isReplay ? 'Gegen KI kämpfen' : 'Nochmal kämpfen' }}</span>
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

    <BattleResultDialog
      v-if="showDialog && report"
      :report="report"
      :my-side="mySide"
      :replay="!!report.timeline"
      :replay-label="reducedMotion && !calm ? 'Animation ansehen' : 'Replay'"
      :again-label="isReplay ? 'Gegen KI kämpfen' : 'Nochmal kämpfen'"
      :note="dialogNote"
      :fresh="!isReplay"
      @close="showDialog = false"
      @again="again"
      @replay="reducedMotion && !calm ? watchCalm() : replay()"
    />
  </main>
</template>

<style scoped>
.mine {
  grid-column: 1;
}

.theirs {
  grid-column: 3;
}

.bey-name {
  margin: 0 0 18px;
  font-size: 2.4rem;
  font-weight: 600;
  line-height: 1.05;
  letter-spacing: -0.035em;
  overflow-wrap: anywhere;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 20px;
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

.arena-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 7px;
  border-radius: 50%;
  vertical-align: 1px;
}

.next-arena {
  display: flex;
  justify-content: center;
  gap: 6px;
}

.next-arena .chip {
  background: var(--bg);
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

.sound-toggle:hover,
.sound-toggle:focus-visible {
  color: var(--text);
  border-color: var(--line-strong);
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

@media (max-width: 640px) {
  .ult-badge-pos {
    bottom: 96px;
    left: 12px;
    transform: scale(0.8);
    transform-origin: bottom left;
  }
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

.callouts {
  position: absolute;
  top: 74px;
  left: 50%;
  display: grid;
  justify-items: center;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
  transform: translateX(-50%);
}

.callouts li {
  padding: 6px 14px;
  border: 1px solid var(--line-strong);
  border-radius: 3px;
  background: var(--bg);
  font-size: 1.15rem;
  font-weight: 600;
  letter-spacing: -0.01em;
  white-space: nowrap;
}

.callouts li.good {
  border-color: var(--accent);
  background: var(--accent);
  color: var(--accent-ink);
}

.callouts li.bad {
  color: var(--text-muted);
}

/* Ult: groß, in der Farbe der Ult, egal ob eigene oder gegnerische */
.callouts li.ult {
  padding: 8px 20px;
  border-color: var(--ult);
  background: color-mix(in srgb, var(--ult) 22%, var(--bg));
  color: var(--text);
  font-size: 1.6rem;
  letter-spacing: -0.02em;
  box-shadow: 0 0 24px color-mix(in srgb, var(--ult) 55%, transparent);
}

.callouts li.ult.good {
  background: var(--ult);
  color: #0b0b0d;
}

.pop-enter-active {
  transition:
    opacity 0.15s,
    transform 0.2s cubic-bezier(0.2, 0.8, 0.2, 1.4);
}

.pop-leave-active {
  transition: opacity 0.3s;
}

.pop-enter-from {
  opacity: 0;
  transform: scale(0.8);
}

.pop-leave-to {
  opacity: 0;
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

.launch-sub {
  padding: 4px 10px;
  border-radius: 3px;
  background: var(--bg);
}

.skip {
  gap: 14px;
  padding: 14px 20px;
  background: var(--bg);
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
  .bey-name {
    font-size: 1.9rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .countdown {
    animation: none;
  }

  .pop-enter-active,
  .pop-leave-active {
    transition: none;
  }
}
</style>
