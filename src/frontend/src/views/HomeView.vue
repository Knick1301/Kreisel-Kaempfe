<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchBeyblades, fetchStats, type BeybladeDto, type PlayerStats } from '@/api'
import { useModelUrls } from '@/composables/useModelUrls'
import { player } from '@/player'
import { hasWebGL } from '@/webgl'
import { pickActive, setActiveId } from '@/activeBeyblade'
import BeybladeSpinner from '@/components/BeybladeSpinner.vue'
import StatBar from '@/components/StatBar.vue'

const ArenaStage = defineAsyncComponent(() => import('@/components/three/ArenaStage.vue'))

const beyblades = ref<BeybladeDto[]>([])
const activeIndex = ref(0)
const beyblade = computed(() => beyblades.value[activeIndex.value] ?? null)

function cycle(step: number) {
  const n = beyblades.value.length
  if (n < 2) return
  activeIndex.value = (activeIndex.value + step + n) % n
  setActiveId(beyblade.value!.id)
  spinKey.value++
}
const loading = ref(true)
const error = ref<string | null>(null)
const spinKey = ref(0)

const router = useRouter()
const playerStats = ref<PlayerStats | null>(null)

onMounted(async () => {
  fetchStats()
    .then((s) => (playerStats.value = typeof s?.winRate === 'number' ? s : null))
    .catch(() => {})
  try {
    const list = await fetchBeyblades()
    beyblades.value = Array.isArray(list) ? list : []
    const active = pickActive(beyblades.value)
    activeIndex.value = Math.max(
      0,
      beyblades.value.findIndex((b) => b.id === active?.id),
    )
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
})

const parts = computed(() =>
  beyblade.value ? [beyblade.value.fusionWheel, beyblade.value.spinTrack, beyblade.value.tip] : [],
)
const urls = useModelUrls(() => parts.value)
const stageUrls = computed(() => (loading.value ? undefined : urls.value))

const partSpecs = computed(() =>
  beyblade.value
    ? [
        { slot: 'Fusion Wheel', name: beyblade.value.fusionWheel.name },
        { slot: 'Spin Track', name: beyblade.value.spinTrack.name },
        { slot: 'Tip', name: beyblade.value.tip.name },
      ]
    : [],
)

const stats = computed(() => {
  const s = beyblade.value?.stats
  if (!s) return []
  return [
    { label: 'Angriff', value: s.attack },
    { label: 'Verteidigung', value: s.defense },
    { label: 'Ausdauer', value: s.stamina },
  ]
})

const modes = [
  {
    id: 'friend',
    label: 'Freundschaftsmatch',
    sub: 'Lobby erstellen oder beitreten',
    to: '/lobby',
  },
  { id: 'tournament', label: 'Turnier', sub: 'Kommt bald', to: null },
] as const

function openMode(to: string | null) {
  if (to) router?.push(to)
}

/** Schnelles Match: sofort in den Kampf gegen einen zufälligen KI-Gegner. */
function startQuickMatch() {
  if (!beyblade.value) return
  setActiveId(beyblade.value.id)
  router?.push({ name: 'kampf', query: { bey: beyblade.value.id } })
}

const leagueProgress = (player.leaguePoints / player.leaguePointsMax) * 100

const webgl = hasWebGL()
</script>

<template>
  <main class="arena-page home">
    <div class="stage" @click="spinKey++">
      <ArenaStage v-if="webgl" :urls="stageUrls" :spin-key="spinKey" :parts="parts" />
      <div v-else class="stage-fallback"><BeybladeSpinner /></div>
    </div>

    <div class="overlay">
      <section class="panel bey" aria-labelledby="bey-name">
        <header class="panel-head">
          <span class="label"><i class="dot" />Dein Beyblade</span>
          <span v-if="beyblades.length > 1" class="switcher">
            <button type="button" aria-label="Vorheriger Kreisel" @click="cycle(-1)">‹</button>
            <span class="label">{{ activeIndex + 1 }}/{{ beyblades.length }}</span>
            <button type="button" aria-label="Nächster Kreisel" @click="cycle(1)">›</button>
          </span>
          <span v-else-if="beyblade" class="label"
            >#{{ String(beyblade.id).padStart(3, '0') }}</span
          >
        </header>

        <div class="panel-body">
          <p v-if="error" class="hint">Backend nicht erreichbar ({{ error }})</p>
          <p v-else-if="loading" class="hint">Lade Beyblade …</p>
          <div v-else-if="!beyblade" class="hint">
            <p>Du hast noch keinen Beyblade gebaut.</p>
            <RouterLink to="/baukasten" class="btn-secondary">Jetzt bauen</RouterLink>
          </div>

          <template v-else>
            <h1 id="bey-name" class="bey-name">{{ beyblade.name }}</h1>

            <dl class="specs">
              <div v-for="p in partSpecs" :key="p.slot">
                <dt>{{ p.slot }}</dt>
                <dd>{{ p.name }}</dd>
              </div>
              <div>
                <dt>Gewicht</dt>
                <dd class="mono">{{ beyblade.stats.weight }}g</dd>
              </div>
            </dl>

            <div class="stats">
              <StatBar v-for="s in stats" :key="s.label" :label="s.label" :value="s.value" />
            </div>

            <RouterLink
              :to="{ path: '/baukasten', query: { id: beyblade.id } }"
              class="btn-secondary"
            >
              Im Baukasten bearbeiten
              <span aria-hidden="true">↗</span>
            </RouterLink>
          </template>
        </div>
      </section>

      <div class="side">
        <section class="panel" aria-label="Spielmodi">
          <header class="panel-head">
            <span class="label"><i class="dot" />Spielmodi</span>
          </header>

          <div class="panel-body modes">
            <button
              v-for="(m, i) in modes"
              :key="m.id"
              type="button"
              class="mode mode-tile"
              :disabled="!m.to"
              @click="openMode(m.to)"
            >
              <span class="mode-no">{{ String(i + 1).padStart(2, '0') }}</span>
              <span class="mode-text">
                <span class="mode-label">{{ m.label }}</span>
                <span class="mode-sub">{{ m.sub }}</span>
              </span>
              <span class="mode-go" aria-hidden="true">→</span>
            </button>
          </div>
        </section>

        <RouterLink to="/rangliste" class="panel league">
          <header class="panel-head">
            <span class="label"><i class="dot" />Liga</span>
            <span class="label">Rangliste ↗</span>
          </header>

          <div class="panel-body">
            <div class="league-head">
              <strong>{{ player.league }}</strong>
              <span class="mono">
                {{ player.leaguePoints }}<span class="faint">/{{ player.leaguePointsMax }}</span>
              </span>
            </div>
            <div class="league-bar"><span :style="{ width: `${leagueProgress}%` }" /></div>
            <div class="league-meta">
              <span
                >Noch {{ player.leaguePointsMax - player.leaguePoints }} bis
                {{ player.nextLeague }}</span
              >
              <span class="mono">
                {{ playerStats ? `${playerStats.winRate} % Siege` : '–' }}
              </span>
            </div>
          </div>
        </RouterLink>
      </div>

      <div class="cta-wrap">
        <button type="button" class="mode cta" :disabled="!beyblade" @click="startQuickMatch">
          <span class="cta-label">Schnelles Match</span>
          <span class="cta-arrow" aria-hidden="true">→</span>
        </button>
        <p class="cta-sub">Zufälliger KI-Gegner · sofort loslegen</p>
      </div>
    </div>
  </main>
</template>

<style scoped>
.bey {
  grid-column: 1;
}

.switcher {
  display: flex;
  align-items: center;
  gap: 4px;
  margin: -6px -10px -6px 0;
}

.switcher button {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border: 1px solid var(--line-strong);
  border-radius: 3px;
  background: none;
  font-size: 1rem;
  line-height: 1;
  color: var(--text-muted);
  cursor: pointer;
  transition:
    color 0.15s,
    border-color 0.15s;
}

.switcher button:hover {
  border-color: var(--text-muted);
  color: var(--text);
}

.switcher .label {
  min-width: 34px;
  text-align: center;
}

.bey-name {
  margin: 0 0 22px;
  font-size: 2.9rem;
  font-weight: 600;
  line-height: 1.05;
  letter-spacing: -0.035em;
  overflow-wrap: anywhere;
}

.side {
  grid-column: 3;
  align-self: start;
  display: grid;
  gap: 14px;
}

.modes {
  display: grid;
  gap: 10px;
  padding: 14px;
}

.mode-tile {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 16px;
  width: 100%;
  padding: 18px 16px;
  border: 1px solid var(--line);
  border-radius: 3px;
  background: #0c0c0e;
  text-align: left;
  cursor: pointer;
  transition:
    background-color 0.15s,
    border-color 0.15s;
}

.mode-no {
  font-family: var(--font-mono);
  font-size: 1.6rem;
  font-weight: 500;
  color: var(--text-faint);
  transition: color 0.15s;
}

.mode-text {
  display: grid;
  gap: 3px;
}

.mode-label {
  font-size: 1.15rem;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.mode-sub {
  font-size: 0.85rem;
  color: var(--text-muted);
}

.mode-go {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid var(--line-strong);
  border-radius: 3px;
  color: var(--text-muted);
  transition:
    background-color 0.15s,
    color 0.15s,
    border-color 0.15s;
}

.mode-tile:disabled {
  cursor: default;
  opacity: 0.5;
}

.mode-tile:not(:disabled):hover {
  border-color: var(--text-faint);
  background: var(--surface-raised);
}

.mode-tile:not(:disabled):hover .mode-no {
  color: var(--text);
}

.mode-tile:not(:disabled):hover .mode-go {
  border-color: var(--text);
  background: var(--text);
  color: var(--bg);
}

.league {
  transition: border-color 0.15s;
}

.league:hover {
  border-color: var(--line-strong);
}

.league-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 12px;
}

.league-head strong {
  font-size: 1.7rem;
  font-weight: 600;
  letter-spacing: -0.02em;
}

.league-bar {
  height: 6px;
  margin-bottom: 10px;
  background: var(--line);
}

.league-bar span {
  display: block;
  height: 100%;
  background: var(--text);
}

.league-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 0.85rem;
  color: var(--text-muted);
}

@media (prefers-reduced-motion: reduce) {
  .mode-tile,
  .mode-go {
    transition: none;
  }
}
</style>
