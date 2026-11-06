<script setup lang="ts">
import { computed, defineAsyncComponent, markRaw, onUnmounted, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ARCHETYPE_LABEL,
  botBattle,
  fetchBattle,
  fetchBeyblades,
  type BattleReport,
  type BeybladeDto,
  type Side,
} from '@/api'
import { auth } from '@/auth'
import { pickActive, setActiveId } from '@/activeBeyblade'
import { hasWebGL } from '@/webgl'
import { createChoreography, type Choreography } from '@/game/choreographer'
import {
  displayEnergy,
  fighterOf,
  formatClock,
  isDeflected,
  matchupLabel,
  mySideOf,
  other,
} from '@/game/playback'
import { useBattlePlayback } from '@/composables/useBattlePlayback'
import { useModelUrls } from '@/composables/useModelUrls'
import BattleResultDialog from '@/components/BattleResultDialog.vue'
import BeybladeSpinner from '@/components/BeybladeSpinner.vue'
import StatBar from '@/components/StatBar.vue'
import BattleLog from '@/components/battle/BattleLog.vue'
import FighterPanel from '@/components/battle/FighterPanel.vue'

const ArenaStage = defineAsyncComponent(() => import('@/components/three/ArenaStage.vue'))
const BattleArena = defineAsyncComponent(() => import('@/components/three/battle/BattleArena.vue'))

type Phase = 'loading' | 'select' | 'drawing' | 'intro' | 'countdown' | 'fight' | 'result' | 'error'

const route = useRoute()
const router = useRouter()

const phase = ref<Phase>('loading')
const error = ref<string | null>(null)
const beyblades = ref<BeybladeDto[]>([])
const selectedId = ref<number | null>(null)
const selected = computed(() => beyblades.value.find((b) => b.id === selectedId.value) ?? null)
const report = shallowRef<BattleReport | null>(null)
const choreo = shallowRef<Choreography | null>(null)
const mySide = ref<Side>('A')
const rolling = ref<string | null>(null)
const countdown = ref<string | null>(null)
const showDialog = ref(false)
const calm = ref(false)
const spinKey = ref(0)

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
const inBattle = computed(() => ['intro', 'countdown', 'fight', 'result'].includes(phase.value))
const hudView = computed(() =>
  phase.value === 'fight' || phase.value === 'result' ? 'hud' : 'intro',
)
const finished = computed(() => phase.value === 'result')

function energy(side: Side) {
  return report.value ? displayEnergy(report.value, side, Math.max(0, time.value)) : 1
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
    selectedId.value = pre?.id ?? null
    phase.value = 'select'
    if (pre && route.query.bey) await draw()
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
  phase.value = 'drawing'
  startRoulette()
  try {
    const [r] = await Promise.all([botBattle(bey.id), wait(reducedMotion ? 200 : 1200)])
    if (token !== run) return
    prepare(r)
    phase.value = 'intro'
  } catch (e) {
    if (token !== run) return
    error.value = e instanceof Error ? e.message : String(e)
    phase.value = 'select'
  } finally {
    stopRoulette()
  }
}

function begin() {
  if (!report.value?.timeline || (reducedMotion && !calm.value)) {
    playback.skip()
    return
  }
  void runCountdown()
}

async function runCountdown() {
  const token = ++run
  showDialog.value = false
  playback.reset()
  phase.value = 'countdown'
  const steps = calm.value || reducedMotion ? ['Los!'] : ['3', '2', '1', 'Los!']
  for (const step of steps) {
    countdown.value = step
    await wait(step === 'Los!' ? 450 : 650)
    if (token !== run) return
  }
  countdown.value = null
  phase.value = 'fight'
  playback.start()
}

function onFightEnd() {
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
      />
      <BattleLog v-else-if="report && inBattle" :report="report" :time="time" :names="names" />
      <ArenaStage
        v-else-if="webgl && selected"
        :urls="phase === 'loading' ? undefined : showcaseUrls"
        :spin-key="spinKey"
      />
      <div v-else class="stage-fallback"><BeybladeSpinner /></div>

      <div class="stage-overlay">
        <p v-if="phase === 'fight' || phase === 'result'" class="clock mono" aria-live="off">
          {{ formatClock(time) }}
        </p>
        <TransitionGroup tag="ul" name="pop" class="callouts" aria-live="polite">
          <li v-for="c in callouts" :key="c.id" :class="c.tone">{{ c.text }}</li>
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
      />
      <section v-else class="panel theirs mystery" aria-label="Gegner">
        <header class="panel-head">
          <span class="label"><i class="dot" />KI-Gegner</span>
        </header>
        <div class="panel-body">
          <h2 class="bey-name faint">? ? ?</h2>
          <p class="hint">
            Wird beim Start gezogen: gleiches Budget wie dein Kreisel, zufällige Werte und ein
            zufälliger Name.
          </p>
        </div>
      </section>

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
            {{ error ?? 'Kampf gegen einen zufälligen KI-Gegner' }}
          </p>
        </template>

        <template v-else-if="phase === 'intro'">
          <button type="button" class="cta" @click="begin">
            <span>{{ reducedMotion && !calm ? 'Ergebnis ansehen' : 'Kampf starten' }}</span>
            <span class="cta-arrow" aria-hidden="true">→</span>
          </button>
          <p class="cta-sub">{{ matchupText }}</p>
        </template>

        <template v-else-if="phase === 'countdown' || phase === 'fight'">
          <button type="button" class="btn-secondary skip" @click="skip">
            Überspringen <span aria-hidden="true">⏭</span>
          </button>
          <p class="cta-sub">{{ report?.arena?.name ?? 'Standard-Stadion' }}</p>
        </template>

        <template v-else-if="phase === 'result'">
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

.mystery .hint {
  max-width: 30ch;
}

.stage-overlay {
  pointer-events: none;
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
