<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ApiError,
  ARCHETYPE_LABEL,
  fetchBeyblades,
  fetchTournament,
  FINISH_LABEL,
  nextTournamentFight,
  startTournament,
  type BeybladeDto,
  type CupEntry,
  type CupPairing,
  type FightMode,
  type TournamentDto,
} from '@/api'
import { pickActive, setActiveId } from '@/activeBeyblade'
import { refreshMe } from '@/stores/me'

/**
 * Solo-Cup: 8er-K.-o.-Baum gegen sieben KI-Gegner, die mit jeder Runde stärker werden. Die eigenen Kämpfe
 * laufen in der Kampfansicht (anspinnen, Ult), danach geht es hierher zurück und der Baum rückt weiter.
 */
const router = useRouter()
const cup = ref<TournamentDto | null>(null)
const beyblades = ref<BeybladeDto[]>([])
const selectedId = ref<number | null>(null)
const loading = ref(true)
const busy = ref(false)
const error = ref<string | null>(null)
/** Klassisch (anspinnen, Ult) oder Arena (Echtzeit-Steuerung) */
const format = ref<FightMode>('CLASSIC')

const LEVEL_LABEL = ['Herausforderer', 'Profi', 'Champion']
const ROUND_TITLES = ['Viertelfinale', 'Halbfinale', 'Finale']

onMounted(async () => {
  try {
    const [list] = await Promise.all([fetchBeyblades(), load()])
    beyblades.value = Array.isArray(list) ? list : []
    selectedId.value = pickActive(beyblades.value)?.id ?? null
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
})

async function load() {
  try {
    cup.value = await fetchTournament()
    if (cup.value.status === 'WON') void refreshMe()
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) cup.value = null
    else throw e
  }
}

async function run(action: () => Promise<void>) {
  busy.value = true
  error.value = null
  try {
    await action()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

function start() {
  const id = selectedId.value
  if (!id) return
  setActiveId(id)
  void run(async () => {
    cup.value = await startTournament(id, format.value)
  })
}

function fight() {
  void run(async () => {
    const t = await nextTournamentFight()
    cup.value = t
    if (!t.matchId) return
    if (t.format === 'ARENA')
      await router?.push({ name: 'arena', query: { room: t.matchId, cup: '1' } })
    else await router?.push({ name: 'kampf', query: { match: t.matchId, cup: '1' } })
  })
}

function newCup() {
  cup.value = null
}

/**
 * Baum in drei Spalten: gespielte Runden mit Siegern, die aktuelle Runde offen, spätere Runden als Platzhalter.
 */
const columns = computed(() => {
  const t = cup.value
  if (!t) return []
  const cols: { title: string; pairs: (CupPairing | null)[] }[] = []
  for (let r = 0; r < 3; r++) {
    const size = 4 >> r
    let pairs: (CupPairing | null)[]
    if (r < t.results.length) pairs = t.results[r]!
    else if (r === t.results.length && t.status === 'RUNNING') pairs = t.upcoming
    else pairs = Array.from({ length: size }, () => null)
    cols.push({ title: ROUND_TITLES[r]!, pairs })
  }
  return cols
})

const champion = computed(() => {
  const t = cup.value
  const final = t?.results[2]?.[0]
  if (!final?.winner) return null
  return final.winner === 'A' ? final.a : final.b
})

const entryName = (e: CupEntry) => (e.you ? `${e.beyblade} (Du)` : e.beyblade)
const isWinner = (p: CupPairing, side: 'A' | 'B') => p.winner === side
const isLoser = (p: CupPairing, side: 'A' | 'B') => !!p.winner && p.winner !== side
</script>

<template>
  <main class="cup-page">
    <div class="cup">
      <header class="page-head">
        <span class="label"><i class="dot" />Turnier</span>
        <h1>Solo-Cup</h1>
        <p class="sub">
          Acht Kreisel, drei Runden, kein Weiterkommen ohne Sieg. Je weiter du kommst, desto stärker
          die Gegner: bessere Teile, besseres Anspinnen. Cup-Kämpfe zählen nicht für die
          Liga-Punkte, der Cup-Sieg bringt Bonus-XP und einen Pokal im Profil.
        </p>
      </header>

      <p v-if="loading" class="hint">Lade Cup …</p>

      <!-- Neuen Cup starten -->
      <section v-else-if="!cup" class="panel setup" aria-labelledby="setup-title">
        <header class="panel-head">
          <span id="setup-title" class="label"><i class="dot" />Neuer Cup</span>
        </header>
        <div class="panel-body">
          <div v-if="beyblades.length === 0" class="hint">
            <p>Du brauchst einen Kreisel.</p>
            <RouterLink to="/baukasten" class="btn-secondary">Jetzt bauen</RouterLink>
          </div>
          <template v-else>
            <div class="chips" role="radiogroup" aria-label="Kreisel wählen">
              <button
                v-for="b in beyblades"
                :key="b.id"
                type="button"
                class="chip"
                role="radio"
                :aria-checked="b.id === selectedId"
                :class="{ active: b.id === selectedId }"
                @click="selectedId = b.id"
              >
                {{ b.name }}
              </button>
            </div>
            <div class="mode-switch" role="radiogroup" aria-label="Format">
              <button
                type="button"
                role="radio"
                :aria-checked="format === 'CLASSIC'"
                :class="{ active: format === 'CLASSIC' }"
                @click="format = 'CLASSIC'"
              >
                Klassisch
              </button>
              <button
                type="button"
                role="radio"
                :aria-checked="format === 'ARENA'"
                :class="{ active: format === 'ARENA' }"
                @click="format = 'ARENA'"
              >
                Arena (selbst steuern)
              </button>
            </div>
            <button type="button" class="cta" :disabled="busy || !selectedId" @click="start">
              <span>Cup starten</span>
              <span class="cta-arrow" aria-hidden="true">→</span>
            </button>
          </template>
        </div>
      </section>

      <template v-else>
        <!-- Status -->
        <section class="panel status" aria-live="polite">
          <div class="panel-body status-body">
            <template v-if="cup.status === 'RUNNING'">
              <div>
                <span class="label"
                  >{{ cup.roundName }} · {{ cup.format === 'ARENA' ? 'Arena' : 'Klassisch' }}</span
                >
                <p class="next">
                  Nächster Gegner: <strong>{{ cup.opponent?.beyblade }}</strong>
                  <small v-if="cup.opponent">{{ ARCHETYPE_LABEL[cup.opponent.archetype] }}</small>
                </p>
              </div>
              <button type="button" class="cta" :disabled="busy" @click="fight">
                <span>{{ cup.matchId ? 'Zum Kampf' : 'Nächster Kampf' }}</span>
                <span class="cta-arrow" aria-hidden="true">→</span>
              </button>
            </template>
            <template v-else-if="cup.status === 'WON'">
              <div class="trophy" aria-hidden="true">🏆</div>
              <div>
                <span class="label">Cup gewonnen</span>
                <p class="next">
                  <strong>Glückwunsch!</strong> +{{ cup.xpReward }} Bonus-XP, der Pokal steht in
                  deinem Profil.
                </p>
              </div>
              <button type="button" class="cta" @click="newCup">
                <span>Neuer Cup</span>
                <span class="cta-arrow" aria-hidden="true">→</span>
              </button>
            </template>
            <template v-else>
              <div>
                <span class="label"
                  >Ausgeschieden im {{ ROUND_TITLES[cup.results.length - 1] }}</span
                >
                <p class="next">
                  <template v-if="champion">Cup-Sieger: {{ entryName(champion) }}.</template>
                  Nächstes Mal klappt's.
                </p>
              </div>
              <button type="button" class="cta" @click="newCup">
                <span>Neuer Cup</span>
                <span class="cta-arrow" aria-hidden="true">→</span>
              </button>
            </template>
          </div>
        </section>

        <!-- Turnierbaum -->
        <section class="bracket" aria-label="Turnierbaum">
          <div v-for="(col, r) in columns" :key="col.title" class="round" :class="`r${r}`">
            <h2 class="label">{{ col.title }}</h2>
            <ol class="pairs">
              <li v-for="(p, i) in col.pairs" :key="i" class="pair" :class="{ empty: !p }">
                <template v-if="p">
                  <div
                    v-for="side in ['A', 'B'] as const"
                    :key="side"
                    class="slot"
                    :class="{
                      you: (side === 'A' ? p.a : p.b).you,
                      won: isWinner(p, side),
                      lost: isLoser(p, side),
                    }"
                  >
                    <span class="name">{{ entryName(side === 'A' ? p.a : p.b) }}</span>
                    <small v-if="(side === 'A' ? p.a : p.b).level !== null" class="lvl">
                      {{ LEVEL_LABEL[(side === 'A' ? p.a : p.b).level!] }}
                    </small>
                  </div>
                  <small v-if="p.finish" class="finish mono">{{ FINISH_LABEL[p.finish] }}</small>
                </template>
                <template v-else>
                  <div class="slot tbd">?</div>
                  <div class="slot tbd">?</div>
                </template>
              </li>
            </ol>
          </div>
        </section>
      </template>

      <p v-if="error" class="error" role="alert">{{ error }}</p>
    </div>
  </main>
</template>

<style scoped>
.cup-page {
  min-height: calc(100svh - var(--header-h));
}

.cup {
  max-width: 1100px;
  margin: 0 auto;
  padding: 40px 32px 72px;
}

.page-head h1 {
  margin: 8px 0 12px;
  font-size: clamp(2.6rem, 6vw, 4.2rem);
  font-weight: 600;
  line-height: 0.95;
  letter-spacing: -0.05em;
}

.sub {
  max-width: 640px;
  margin: 0 0 28px;
  color: var(--text-muted);
  line-height: 1.55;
}

.setup .panel-body {
  display: grid;
  justify-items: start;
  gap: 20px;
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

.status {
  margin-bottom: 20px;
}

.mode-switch {
  display: inline-flex;
  gap: 4px;
  padding: 3px;
  border: 1px solid var(--line);
  border-radius: 4px;
}

.mode-switch button {
  padding: 6px 12px;
  border: 0;
  border-radius: 3px;
  background: none;
  font-size: 0.85rem;
  color: var(--text-muted);
  cursor: pointer;
}

.mode-switch button.active {
  background: var(--line);
  color: var(--text);
}

.status-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 20px;
}

.next {
  margin: 6px 0 0;
  font-size: 1.15rem;
}

.next small {
  margin-left: 8px;
  font-size: 0.8rem;
  color: var(--text-muted);
}

.trophy {
  font-size: 3rem;
}

.bracket {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.round {
  display: grid;
  grid-template-rows: auto 1fr;
  gap: 10px;
}

.pairs {
  display: grid;
  align-content: space-around;
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.pair {
  display: grid;
  gap: 2px;
  padding: 8px;
  border: 1px solid var(--line);
  border-radius: 4px;
  background: var(--surface, var(--bg));
}

.slot {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 3px;
}

.slot .name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.slot.you {
  outline: 1px solid var(--accent);
}

.slot.won {
  background: var(--line);
  font-weight: 600;
}

.slot.lost {
  color: var(--text-faint);
  text-decoration: line-through;
}

.slot.tbd {
  color: var(--text-faint);
}

.lvl {
  font-size: 0.7rem;
  color: var(--text-faint);
}

.finish {
  padding: 0 8px;
  font-size: 0.7rem;
  color: var(--text-muted);
}

.hint {
  color: var(--text-muted);
}

.error {
  margin-top: 16px;
  color: #ef6a5b;
}

@media (max-width: 760px) {
  .cup {
    padding: 28px 16px 48px;
  }

  .bracket {
    grid-template-columns: 1fr;
  }
}
</style>
