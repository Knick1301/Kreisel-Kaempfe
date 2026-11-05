<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { fetchLeaderboard, fetchStats, type Leaderboard, type PlayerStats } from '@/api'
import { player } from '@/player'

const fmt = new Intl.NumberFormat('de-DE')
const rankNo = (n: number) => (n < 100 ? String(n).padStart(2, '0') : fmt.format(n))

const board = ref<Leaderboard | null>(null)
const stats = ref<PlayerStats | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

onMounted(async () => {
  try {
    const [b, s] = await Promise.all([fetchLeaderboard(10), fetchStats()])
    board.value = b
    stats.value = s
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
})

const kpis = computed(() => {
  const s = stats.value
  return [
    { label: 'Siege', value: s ? fmt.format(s.wins) : '–' },
    { label: 'Niederlagen', value: s ? fmt.format(s.losses) : '–' },
    { label: 'Siegquote', value: s ? String(s.winRate) : '–', unit: s ? '%' : '' },
  ]
})

const meBelow = computed(() => {
  const b = board.value
  if (!b?.me) return null
  return b.entries.some((e) => e.username === b.me!.username) ? null : b.me
})

const progress = (player.leaguePoints / player.leaguePointsMax) * 100

const ticks = Array.from({ length: 120 }, (_, i) => ({
  angle: i * 3,
  major: i % 10 === 0,
}))
</script>

<template>
  <main class="ranking-page">
    <svg class="backdrop" viewBox="-100 -100 200 200" aria-hidden="true">
      <circle r="96" />
      <circle r="70" />
      <line
        v-for="t in ticks"
        :key="t.angle"
        :class="{ major: t.major }"
        :transform="`rotate(${t.angle})`"
        x1="0"
        :y1="t.major ? -92 : -88"
        x2="0"
        y2="-84"
      />
    </svg>

    <div class="ranking">
      <header class="page-head">
        <div>
          <span class="label"><i class="dot" />Sortiert nach Siegen</span>
          <h1>Rangliste</h1>
        </div>
        <div class="my-rank">
          <span class="label">Dein Rang</span>
          <strong class="mono">{{ stats?.rank ? `#${fmt.format(stats.rank)}` : '–' }}</strong>
        </div>
      </header>

      <section class="kpis" aria-label="Deine Statistik">
        <div v-for="k in kpis" :key="k.label" class="panel kpi">
          <div class="panel-body">
            <span class="label">{{ k.label }}</span>
            <strong class="kpi-value mono">
              {{ k.value }}<small v-if="k.unit">{{ k.unit }}</small>
            </strong>
          </div>
        </div>

        <div class="panel kpi league">
          <div class="panel-body">
            <div class="league-head">
              <span class="label">Liga · Platzhalter</span>
              <span class="mono faint">{{ player.leaguePoints }}/{{ player.leaguePointsMax }}</span>
            </div>
            <strong class="league-name">{{ player.league }}</strong>
            <div class="league-bar"><span :style="{ width: `${progress}%` }" /></div>
            <span class="league-sub">
              Noch {{ player.leaguePointsMax - player.leaguePoints }} Punkte bis
              {{ player.nextLeague }}
            </span>
          </div>
        </div>
      </section>

      <section class="panel board" aria-label="Top-Spieler">
        <header class="panel-head">
          <span class="label"><i class="dot" />Top-Spieler</span>
          <span v-if="board" class="label">{{ board.totalPlayers }} Spieler</span>
        </header>

        <p v-if="error" class="board-hint">Backend nicht erreichbar ({{ error }})</p>
        <p v-else-if="loading" class="board-hint">Lade Rangliste …</p>
        <div v-else-if="board?.empty" class="board-empty">
          <strong>Noch keine Kämpfe</strong>
          <p>Sobald der erste Kampf gespeichert ist, erscheint hier die Rangliste.</p>
          <RouterLink to="/" class="btn-secondary">Zum Schnellen Match <span>→</span></RouterLink>
        </div>

        <table v-else-if="board">
          <thead>
            <tr>
              <th class="col-rank">Rang</th>
              <th>Spieler</th>
              <th>Bilanz</th>
              <th class="col-wins">Siege</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in board.entries"
              :key="row.username"
              :class="{ top: row.rank <= 3, me: row.username === board.me?.username }"
            >
              <td class="col-rank mono">{{ rankNo(row.rank) }}</td>
              <td class="player">
                {{ row.username }}
                <span v-if="row.username === board.me?.username" class="chip you">Du</span>
              </td>
              <td>
                <span class="chip">{{ row.wins }}S · {{ row.losses }}N</span>
              </td>
              <td class="col-wins mono">{{ fmt.format(row.wins) }}</td>
            </tr>
            <template v-if="meBelow">
              <tr class="gap" aria-hidden="true">
                <td colspan="4">· · ·</td>
              </tr>
              <tr class="me">
                <td class="col-rank mono">{{ rankNo(meBelow.rank) }}</td>
                <td class="player">
                  {{ meBelow.username }}
                  <span class="chip you">Du</span>
                </td>
                <td>
                  <span class="chip">{{ meBelow.wins }}S · {{ meBelow.losses }}N</span>
                </td>
                <td class="col-wins mono">{{ fmt.format(meBelow.wins) }}</td>
              </tr>
            </template>
          </tbody>
        </table>
      </section>
    </div>
  </main>
</template>

<style scoped>
.ranking-page {
  position: relative;
  overflow: hidden;
  min-height: calc(100svh - var(--header-h));
}

.ranking {
  position: relative;
  max-width: 1240px;
  margin: 0 auto;
  padding: 48px 32px 72px;
}

.backdrop {
  position: absolute;
  top: -220px;
  right: -240px;
  width: 760px;
  fill: none;
  stroke: var(--line);
  stroke-width: 0.4;
  pointer-events: none;
}

.backdrop line {
  stroke: var(--line);
}

.backdrop line.major {
  stroke: var(--line-strong);
  stroke-width: 0.6;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 32px;
}

.page-head h1 {
  margin: 10px 0 0;
  font-size: clamp(3rem, 7vw, 5rem);
  font-weight: 600;
  line-height: 0.95;
  letter-spacing: -0.05em;
}

.my-rank {
  display: grid;
  justify-items: end;
  gap: 6px;
}

.my-rank strong {
  font-size: 2rem;
  font-weight: 500;
}

.kpis {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr)) minmax(0, 1.6fr);
  gap: 14px;
  margin-bottom: 14px;
}

.kpi {
  align-self: stretch;
}

.kpi .panel-body {
  display: grid;
  gap: 14px;
  height: 100%;
}

.kpi-value {
  font-size: 3rem;
  font-weight: 500;
  line-height: 1;
  letter-spacing: -0.04em;
}

.kpi-value small {
  margin-left: 4px;
  font-size: 1.4rem;
  color: var(--text-muted);
}

.league .panel-body {
  gap: 10px;
}

.league-head {
  display: flex;
  justify-content: space-between;
  font-size: 0.8rem;
}

.league-name {
  font-size: 1.8rem;
  font-weight: 600;
  letter-spacing: -0.03em;
}

.league-bar {
  height: 6px;
  background: var(--line);
}

.league-bar span {
  display: block;
  height: 100%;
  background: var(--text);
}

.league-sub {
  font-size: 0.85rem;
  color: var(--text-muted);
}

.board-hint {
  margin: 0;
  padding: 28px 22px;
  color: var(--text-muted);
}

.board-empty {
  display: grid;
  justify-items: start;
  gap: 10px;
  padding: 40px 22px 32px;
}

.board-empty strong {
  font-size: 2rem;
  font-weight: 600;
  letter-spacing: -0.03em;
}

.board-empty p {
  margin: 0 0 10px;
  color: var(--text-muted);
}

.board-empty .btn-secondary {
  gap: 24px;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th {
  padding: 14px 22px;
  border-bottom: 1px solid var(--line);
  font-family: var(--font-mono);
  font-size: 0.7rem;
  font-weight: 400;
  letter-spacing: 0.08em;
  text-align: left;
  text-transform: uppercase;
  color: var(--text-faint);
}

td {
  padding: 18px 22px;
  border-bottom: 1px solid var(--line);
  vertical-align: middle;
}

.col-rank {
  width: 140px;
}

td.col-rank {
  font-size: 1.6rem;
  font-weight: 500;
  color: var(--text-faint);
}

tr.top td.col-rank {
  color: var(--text);
}

.col-wins {
  text-align: right;
}

td.col-wins {
  font-size: 1.1rem;
}

.player {
  font-size: 1.1rem;
  font-weight: 600;
  letter-spacing: -0.01em;
}

tbody tr:not(.gap, .me) {
  transition: background-color 0.15s;
}

tbody tr:not(.gap, .me):hover {
  background: rgba(255, 255, 255, 0.025);
}

.chip {
  display: inline-block;
  padding: 4px 8px;
  border: 1px solid var(--line-strong);
  border-radius: 2px;
  font-family: var(--font-mono);
  font-size: 0.7rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-muted);
}

tr.gap td {
  padding: 8px 22px;
  font-family: var(--font-mono);
  letter-spacing: 0.4em;
  color: var(--text-faint);
}

tr.me td {
  border-bottom: 0;
  background: var(--text);
  color: var(--bg);
}

tr.me td.col-rank {
  color: var(--bg);
}

tr.me .chip {
  border-color: rgba(10, 10, 11, 0.35);
  color: var(--bg);
}

.chip.you {
  margin-left: 10px;
  vertical-align: 2px;
}

@media (max-width: 960px) {
  .kpis {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .kpis .league {
    grid-column: 1 / -1;
  }
}

@media (max-width: 640px) {
  .ranking {
    padding: 32px 16px 48px;
  }

  .page-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .my-rank {
    justify-items: start;
  }

  .kpis {
    grid-template-columns: 1fr;
  }

  .kpi-value {
    font-size: 2.4rem;
  }

  th,
  td {
    padding-right: 12px;
    padding-left: 12px;
  }

  .col-rank {
    width: 64px;
  }

  td.col-rank {
    font-size: 1.2rem;
  }

  th:nth-child(3),
  td:nth-child(3) {
    display: none;
  }
}
</style>
