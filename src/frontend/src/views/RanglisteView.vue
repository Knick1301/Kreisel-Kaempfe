<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  fetchArenaLeaderboard,
  fetchLeaderboard,
  type Leaderboard,
  type LeaderboardEntry,
} from '@/api'
import { me, refreshMe } from '@/stores/me'
import LeagueBadge from '@/components/LeagueBadge.vue'
import { allDivisions, lp, type Division } from '@/game/league'

const fmt = new Intl.NumberFormat('de-DE')
const rankNo = (n: number) => (n < 100 ? String(n).padStart(2, '0') : fmt.format(n))

const board = ref<Leaderboard | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

/** Klassisch (berechnete Kämpfe) oder Arena-Modus (Physik, eigener Rang) */
type Mode = 'classic' | 'arena'
const route = useRoute()
const router = useRouter()
const mode = ref<Mode>(route?.query.modus === 'arena' ? 'arena' : 'classic')
function setMode(m: Mode) {
  mode.value = m
  void router?.replace({ query: m === 'arena' ? { modus: 'arena' } : {} })
}

let token = 0
async function load(m: Mode) {
  const mine = ++token
  loading.value = true
  error.value = null
  board.value = null
  try {
    const [b] = await Promise.all([
      m === 'arena' ? fetchArenaLeaderboard(25) : fetchLeaderboard(25),
      refreshMe(),
    ])
    if (mine === token) board.value = b
  } catch (e) {
    if (mine === token) error.value = e instanceof Error ? e.message : String(e)
  } finally {
    if (mine === token) loading.value = false
  }
}
watch(mode, load, { immediate: true })

const stats = computed(() => me.stats)
/** Arena: eigener Eintrag aus der Arena-Rangliste, null ohne Arena-Kampf */
const arenaMe = computed(() => (mode.value === 'arena' ? (board.value?.me ?? null) : null))
const rank = computed(() => (mode.value === 'arena' ? (arenaMe.value?.ranking ?? null) : me.rank))
const place = computed(() => (mode.value === 'arena' ? arenaMe.value?.rank : stats.value?.rank))

const rate = (wins: number, losses: number) => ({
  wins,
  losses,
  rate: wins + losses === 0 ? null : Math.round((100 * wins) / (wins + losses)),
})

/** Bilanz über alle Kämpfe, auch gegen die KI */
const totals = computed(() => {
  if (mode.value === 'arena')
    return arenaMe.value ? rate(arenaMe.value.wins, arenaMe.value.losses) : null
  const s = stats.value
  if (!s) return null
  return rate(s.wins + (s.botWins ?? 0), s.losses + (s.botLosses ?? 0))
})

/** Tabellenzeilen: Top-Spieler, dazu die eigene Zeile hinter einer Lücke, falls sie nicht dabei ist. */
type Row = { kind: 'entry'; entry: LeaderboardEntry; mine: boolean } | { kind: 'gap' }
const rows = computed<Row[]>(() => {
  const b = board.value
  if (!b) return []
  const myName = b.me?.username
  const list: Row[] = b.entries.map((entry) => ({
    kind: 'entry',
    entry,
    mine: entry.username === myName,
  }))
  if (b.me && !b.entries.some((e) => e.username === myName)) {
    list.push({ kind: 'gap' }, { kind: 'entry', entry: b.me, mine: true })
  }
  return list
})

/** Rangleiter: Ligen von oben (Meister) nach unten, je mit ihren Divisionen */
const ladder = computed(() => {
  const groups: { tier: string; divisions: Division[] }[] = []
  for (const d of allDivisions()) {
    const last = groups[groups.length - 1]
    if (last?.tier === d.tier) last.divisions.push(d)
    else groups.push({ tier: d.tier, divisions: [d] })
  }
  // innerhalb einer Liga von links III → I, damit es von links nach rechts aufwärts geht
  for (const g of groups) g.divisions.reverse()
  return groups
})
</script>

<template>
  <main class="ranking-page">
    <div class="ranking">
      <header class="page-head">
        <span class="label"><i class="dot" />Sortiert nach Liga-Punkten</span>
        <h1>Rangliste</h1>
        <div class="mode-switch" role="radiogroup" aria-label="Rangliste wählen">
          <button
            type="button"
            role="radio"
            :aria-checked="mode === 'classic'"
            :class="{ active: mode === 'classic' }"
            @click="setMode('classic')"
          >
            Klassisch
          </button>
          <button
            type="button"
            role="radio"
            :aria-checked="mode === 'arena'"
            :class="{ active: mode === 'arena' }"
            @click="setMode('arena')"
          >
            Arena-Modus
          </button>
        </div>
      </header>

      <div class="layout">
        <!-- Tabelle -->
        <section class="panel board" aria-label="Top-Spieler">
          <header class="panel-head">
            <span class="label"><i class="dot" />Top-Spieler</span>
            <span v-if="board" class="label">{{ fmt.format(board.totalPlayers) }} Spieler</span>
          </header>

          <p v-if="error" class="board-hint">Backend nicht erreichbar ({{ error }})</p>
          <p v-else-if="loading" class="board-hint">Lade Rangliste …</p>
          <div v-else-if="board?.empty" class="board-empty">
            <strong>Noch keine Kämpfe</strong>
            <p>Sobald jemand gewertet gekämpft hat, erscheint hier die Rangliste.</p>
            <RouterLink v-if="mode === 'arena'" to="/arena" class="btn-secondary"
              >Zum Arena-Modus <span>→</span></RouterLink
            >
            <RouterLink v-else to="/" class="btn-secondary"
              >Zum Schnellen Match <span>→</span></RouterLink
            >
          </div>

          <table v-else-if="board">
            <thead>
              <tr>
                <th class="col-rank">Rang</th>
                <th>Spieler</th>
                <th>Liga</th>
                <th class="col-record">Bilanz</th>
                <th class="col-lp">LP</th>
              </tr>
            </thead>
            <tbody>
              <template
                v-for="(row, i) in rows"
                :key="row.kind === 'gap' ? `gap-${i}` : row.entry.username"
              >
                <tr v-if="row.kind === 'gap'" class="gap" aria-hidden="true">
                  <td colspan="5">· · ·</td>
                </tr>
                <tr v-else :class="{ top: row.entry.rank <= 3, me: row.mine }">
                  <td class="col-rank mono">{{ rankNo(row.entry.rank) }}</td>
                  <td class="player">
                    {{ row.entry.username }}
                    <span v-if="row.mine" class="chip you">Du</span>
                  </td>
                  <td>
                    <span class="league-cell">
                      <LeagueBadge
                        :tier="row.entry.ranking.tier"
                        :division="row.entry.ranking.division"
                        :size="22"
                      />
                      {{ row.entry.ranking.league }}
                    </span>
                  </td>
                  <td class="col-record mono">{{ row.entry.wins }}S · {{ row.entry.losses }}N</td>
                  <td class="col-lp mono">{{ fmt.format(lp(row.entry.ranking.rating)) }}</td>
                </tr>
              </template>
            </tbody>
          </table>
        </section>

        <aside class="side">
          <!-- Eigener Rang -->
          <section class="panel mine" aria-labelledby="mine-title">
            <header class="panel-head">
              <span id="mine-title" class="label"><i class="dot" />Dein Rang</span>
              <span v-if="rank" class="label mono">Level {{ rank.level }}</span>
            </header>
            <div class="panel-body">
              <template v-if="rank">
                <div class="mine-head">
                  <LeagueBadge :tier="rank.tier" :division="rank.division" :size="48" />
                  <div>
                    <strong class="mine-league">{{ rank.league }}</strong>
                    <span class="mine-lp mono">{{ fmt.format(lp(rank.rating)) }} LP</span>
                  </div>
                  <span class="mine-place mono">
                    {{ place ? `#${fmt.format(place)}` : '–' }}
                    <small v-if="board">von {{ fmt.format(board.totalPlayers) }}</small>
                  </span>
                </div>
                <div class="bar"><span :style="{ width: `${rank.progress}%` }" /></div>
                <p class="mine-sub">
                  <template v-if="rank.nextLeague">
                    Noch {{ rank.pointsToNext }} LP bis {{ rank.nextLeague }}
                  </template>
                  <template v-else>Höchste Liga erreicht</template>
                </p>
                <dl class="mine-stats">
                  <div>
                    <dt>Siege</dt>
                    <dd class="mono">{{ totals ? fmt.format(totals.wins) : '–' }}</dd>
                  </div>
                  <div>
                    <dt>Niederlagen</dt>
                    <dd class="mono">{{ totals ? fmt.format(totals.losses) : '–' }}</dd>
                  </div>
                  <div>
                    <dt>Siegquote</dt>
                    <dd class="mono">{{ totals?.rate != null ? `${totals.rate} %` : '–' }}</dd>
                  </div>
                </dl>
              </template>
              <p v-else-if="mode === 'arena' && !loading" class="mine-sub">
                Noch kein Arena-Kampf.
                <RouterLink to="/arena">Jetzt selbst steuern →</RouterLink>
              </p>
              <p v-else class="mine-sub">Lade …</p>
            </div>
          </section>

          <!-- Rangleiter -->
          <section class="panel tiers" aria-labelledby="tiers-title">
            <header class="panel-head">
              <span id="tiers-title" class="label"><i class="dot" />Alle Ränge</span>
              <span class="label">ab LP</span>
            </header>
            <ol class="ladder">
              <li v-for="g in ladder" :key="g.tier" class="tier-row">
                <span class="tier-name">{{ g.tier }}</span>
                <span class="tier-steps">
                  <span
                    v-for="d in g.divisions"
                    :key="d.name"
                    class="step"
                    :class="{ current: rank?.league === d.name }"
                    :aria-current="rank?.league === d.name ? 'true' : undefined"
                    :title="`${d.name} ab ${fmt.format(d.fromLp)} LP`"
                  >
                    <LeagueBadge :tier="d.tier" :division="d.division" :size="22" />
                    <small class="mono">{{ fmt.format(d.fromLp) }}</small>
                  </span>
                </span>
              </li>
            </ol>
            <p v-if="mode === 'arena'" class="tiers-note">
              Eigener Rang für den Arena-Modus, gleiche Ligen. Gegen die KI gibt es Liga-Punkte nur
              bis Gold III.
            </p>
            <p v-else class="tiers-note">
              Gegen die KI gibt es Liga-Punkte nur bis Gold III, darüber zählen nur Kämpfe gegen
              Spieler. Unter 0 LP fällt niemand.
            </p>
          </section>
        </aside>
      </div>
    </div>
  </main>
</template>

<style scoped>
.ranking-page {
  min-height: calc(100svh - var(--header-h));
}

.ranking {
  max-width: 1240px;
  margin: 0 auto;
  padding: 40px 32px 72px;
}

.page-head {
  margin-bottom: 24px;
}

.page-head h1 {
  margin: 8px 0 0;
  font-size: clamp(2.6rem, 6vw, 4.2rem);
  font-weight: 600;
  line-height: 0.95;
  letter-spacing: -0.05em;
}

.mode-switch {
  display: inline-flex;
  gap: 4px;
  margin-top: 16px;
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

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 16px;
  align-items: start;
}

.side {
  display: grid;
  gap: 16px;
}

/* ---------------------------------------------------------------- Eigener Rang */
.mine .panel-body {
  display: grid;
  gap: 12px;
}

.mine-head {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 12px;
}

.mine-league {
  display: block;
  font-size: 1.35rem;
  font-weight: 600;
  letter-spacing: -0.02em;
}

.mine-lp {
  font-size: 0.85rem;
  color: var(--text-muted);
}

.mine-place {
  display: grid;
  justify-items: end;
  font-size: 1.5rem;
  font-weight: 500;
}

.mine-place small {
  font-size: 0.7rem;
  color: var(--text-faint);
}

.bar {
  height: 6px;
  background: var(--line);
}

.bar span {
  display: block;
  height: 100%;
  background: var(--text);
}

.mine-sub {
  margin: 0;
  font-size: 0.85rem;
  color: var(--text-muted);
}

.mine-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  margin: 4px 0 0;
  border-top: 1px solid var(--line);
}

.mine-stats div {
  padding-top: 10px;
}

.mine-stats dt {
  font-size: 0.7rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-faint);
}

.mine-stats dd {
  margin: 2px 0 0;
  font-size: 1.15rem;
}

/* ---------------------------------------------------------------- Rangleiter */
.ladder {
  display: grid;
  margin: 0;
  padding: 6px 12px;
  list-style: none;
}

.tier-row {
  display: grid;
  grid-template-columns: 76px 1fr;
  align-items: center;
  padding: 6px 0;
  border-bottom: 1px solid var(--line);
}

.tier-row:last-child {
  border-bottom: 0;
}

.tier-name {
  font-size: 0.85rem;
  font-weight: 600;
}

.tier-steps {
  display: flex;
  gap: 4px;
}

.step {
  display: grid;
  flex: 1;
  justify-items: center;
  gap: 2px;
  padding: 4px 2px;
  border: 1px solid transparent;
  border-radius: 3px;
}

.step small {
  font-size: 0.65rem;
  color: var(--text-faint);
}

.step.current {
  border-color: var(--accent);
  background: color-mix(in srgb, var(--accent) 12%, transparent);
}

.step.current small {
  color: var(--text);
}

.tiers-note {
  margin: 0;
  padding: 0 14px 14px;
  font-size: 0.75rem;
  color: var(--text-faint);
}

/* ---------------------------------------------------------------- Tabelle */
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
  padding: 12px 18px;
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
  padding: 14px 18px;
  border-bottom: 1px solid var(--line);
  vertical-align: middle;
}

.col-rank {
  width: 84px;
}

td.col-rank {
  font-size: 1.3rem;
  font-weight: 500;
  color: var(--text-faint);
}

tr.top td.col-rank {
  color: var(--text);
}

.col-record {
  white-space: nowrap;
  color: var(--text-muted);
}

.col-lp {
  text-align: right;
}

td.col-lp {
  font-size: 1.05rem;
}

.player {
  font-size: 1.05rem;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.league-cell {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-size: 0.9rem;
  font-weight: 500;
}

tbody tr:not(.gap, .me) {
  transition: background-color 0.15s;
}

tbody tr:not(.gap, .me):hover {
  background: rgba(255, 255, 255, 0.025);
}

.chip {
  display: inline-block;
  padding: 3px 7px;
  border: 1px solid var(--line-strong);
  border-radius: 2px;
  font-family: var(--font-mono);
  font-size: 0.68rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.chip.you {
  margin-left: 8px;
  vertical-align: 2px;
}

tr.gap td {
  padding: 6px 18px;
  font-family: var(--font-mono);
  letter-spacing: 0.4em;
  color: var(--text-faint);
}

tr.me td {
  border-bottom: 0;
  background: var(--text);
  color: var(--bg);
}

tr.me td.col-rank,
tr.me .col-record {
  color: var(--bg);
}

tr.me .chip {
  border-color: rgba(10, 10, 11, 0.35);
  color: var(--bg);
}

/* ---------------------------------------------------------------- schmal */
@media (max-width: 1000px) {
  .layout {
    grid-template-columns: 1fr;
  }

  /* eigener Rang zuerst, Tabelle danach */
  .side {
    order: -1;
    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  }
}

@media (max-width: 640px) {
  .ranking {
    padding: 28px 16px 48px;
  }

  th,
  td {
    padding-right: 10px;
    padding-left: 10px;
  }

  .col-rank {
    width: 52px;
  }

  td.col-rank {
    font-size: 1.05rem;
  }

  .col-record {
    display: none;
  }
}
</style>
