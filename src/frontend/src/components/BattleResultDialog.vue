<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { ARCHETYPE_LABEL, FINISH_LABEL, type BattleReport, type Side } from '@/api'
import { auth } from '@/auth'
import { fighterOf, formatClock, matchupLabel, other } from '@/game/playback'
import { leagueChange, leagueOf } from '@/game/league'
import { me as meStore } from '@/stores/me'
import LeagueBadge from './LeagueBadge.vue'

const props = withDefaults(
  defineProps<{
    report: BattleReport
    again?: boolean
    /** Seite des Spielers; ohne Angabe über den Benutzernamen bestimmt */
    mySide?: Side
    /** Button für die Wiedergabe anzeigen (gleicher Seed, kein neuer Kampf) */
    replay?: boolean
    replayLabel?: string
    againLabel?: string
    note?: string | null
    /** Kampf gerade ausgetragen: Liga-Punkte und neue Liga anzeigen */
    fresh?: boolean
  }>(),
  {
    again: true,
    mySide: undefined,
    replay: false,
    replayLabel: 'Replay',
    againLabel: 'Nochmal kämpfen',
    note: null,
    fresh: false,
  },
)
const emit = defineEmits<{ close: []; again: []; replay: [] }>()

const won = computed(() =>
  props.mySide && props.report.winnerSide
    ? props.report.winnerSide === props.mySide
    : props.report.winner === auth.username,
)
const side = computed<Side>(() => {
  if (props.mySide) return props.mySide
  const w = props.report.winnerSide
  return w ? (won.value ? w : other(w)) : 'A'
})
const duration = computed(() =>
  props.report.durationMs != null
    ? formatClock(props.report.durationMs)
    : `Runde ${props.report.rounds}`,
)
const hits = computed(() => {
  const h = props.report.timeline?.hits
  return h ? { mine: h[side.value], theirs: h[other(side.value)] } : null
})
const matchup = computed(() => {
  const mine = fighterOf(props.report, side.value)
  const theirs = fighterOf(props.report, other(side.value))
  if (!mine || !theirs) return null
  return `${ARCHETYPE_LABEL[mine.archetype]} vs ${ARCHETYPE_LABEL[theirs.archetype]} · ${matchupLabel(props.report.matchup, side.value)}`
})
const me = computed(() =>
  won.value
    ? { name: props.report.winner, bey: props.report.winnerBeyblade }
    : { name: props.report.loser, bey: props.report.loserBeyblade },
)
const opponent = computed(() =>
  won.value
    ? { name: props.report.loser, bey: props.report.loserBeyblade }
    : { name: props.report.winner, bey: props.report.winnerBeyblade },
)

const delta = computed(
  () => (won.value ? props.report.ratingDeltaWinner : props.report.ratingDeltaLoser) ?? null,
)
const rank = computed(() => (props.fresh ? meStore.rank : null))
const change = computed(() => {
  const r = rank.value
  if (!r || delta.value === null) return 0
  return leagueChange(r.rating - delta.value, r.rating)
})
const previousLeague = computed(() =>
  rank.value && delta.value !== null ? leagueOf(rank.value.rating - delta.value) : null,
)
const signed = (n: number) => (n > 0 ? `+${n}` : n < 0 ? `−${Math.abs(n)}` : '±0')

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('close')
}
onMounted(() => window.addEventListener('keydown', onKey))
onUnmounted(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <div class="backdrop" @click.self="emit('close')">
    <section class="panel dialog" role="dialog" aria-modal="true" aria-labelledby="result-title">
      <header class="panel-head">
        <span class="label"><i class="dot" />Kampfergebnis</span>
        <span class="label">#{{ String(report.id).padStart(4, '0') }}</span>
      </header>

      <div class="panel-body">
        <h2 id="result-title" class="verdict" :class="{ lost: !won }">
          {{ won ? 'Sieg' : 'Niederlage' }}
        </h2>
        <p class="how mono">{{ FINISH_LABEL[report.finish] }} · {{ duration }}</p>

        <dl v-if="hits || matchup" class="facts">
          <div v-if="hits">
            <dt>Treffer</dt>
            <dd class="mono">Du {{ hits.mine }} · Gegner {{ hits.theirs }}</dd>
          </div>
          <div v-if="matchup">
            <dt>Matchup</dt>
            <dd>{{ matchup }}</dd>
          </div>
        </dl>
        <p v-if="note" class="note">{{ note }}</p>

        <div v-if="rank" class="ranking" :class="{ promoted: change > 0, demoted: change < 0 }">
          <LeagueBadge :tier="rank.tier" :division="rank.division" :size="40" class="badge" />
          <div class="ranking-text">
            <span class="ranking-head">
              <strong>{{ rank.league }}</strong>
              <span v-if="delta !== null" class="lp mono">{{ signed(delta) }} LP</span>
              <span v-else class="lp mono faint">keine Liga-Punkte</span>
            </span>
            <div class="ranking-bar"><span :style="{ width: `${rank.progress}%` }" /></div>
            <span class="ranking-sub">
              <template v-if="change > 0">Aufstieg aus {{ previousLeague }}!</template>
              <template v-else-if="change < 0">Abstieg aus {{ previousLeague }}</template>
              <template v-else-if="rank.nextLeague">
                Noch {{ rank.pointsToNext }} bis {{ rank.nextLeague }}
              </template>
              <template v-else>Höchste Liga</template>
            </span>
          </div>
        </div>

        <div class="versus">
          <div class="side" :class="{ winner: won }">
            <span class="label">Du</span>
            <strong>{{ me.bey }}</strong>
            <span class="who">{{ me.name }}</span>
          </div>
          <span class="vs mono">VS</span>
          <div class="side right" :class="{ winner: !won }">
            <span class="label">Gegner</span>
            <strong>{{ opponent.bey }}</strong>
            <span class="who">{{ opponent.name }}</span>
          </div>
        </div>

        <div class="actions">
          <button type="button" class="btn-secondary" @click="emit('close')">Schließen</button>
          <button v-if="replay" type="button" class="btn-secondary" @click="emit('replay')">
            {{ replayLabel }}
          </button>
          <button v-if="again" type="button" class="cta small" @click="emit('again')">
            <span>{{ againLabel }}</span>
            <span class="cta-arrow" aria-hidden="true">→</span>
          </button>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.ranking {
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 0 0 22px;
  padding: 14px 16px;
  border: 1px solid var(--line);
  border-radius: 3px;
  background: #0c0c0e;
}

.ranking.promoted {
  border-color: var(--text);
  animation: promote 1.2s cubic-bezier(0.2, 0.8, 0.2, 1);
}

.ranking.promoted .badge {
  animation: badge-pop 0.9s cubic-bezier(0.2, 1.6, 0.4, 1) 0.2s both;
}

.ranking-text {
  display: grid;
  flex: 1;
  gap: 8px;
}

.ranking-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.ranking-head strong {
  font-size: 1.15rem;
  font-weight: 600;
}

.lp {
  font-size: 0.95rem;
}

.faint {
  color: var(--text-faint);
  font-size: 0.78rem;
}

.ranking-bar {
  height: 4px;
  background: var(--line);
}

.ranking-bar span {
  display: block;
  height: 100%;
  background: var(--text);
  transition: width 0.8s cubic-bezier(0.2, 0.8, 0.2, 1);
}

.ranking-sub {
  font-size: 0.8rem;
  color: var(--text-muted);
}

.promoted .ranking-sub {
  color: var(--text);
  font-weight: 600;
}

@keyframes promote {
  0% {
    box-shadow: 0 0 0 0 rgba(237, 237, 239, 0.5);
  }
  100% {
    box-shadow: 0 0 0 14px rgba(237, 237, 239, 0);
  }
}

@keyframes badge-pop {
  from {
    transform: scale(0.4) rotate(-90deg);
    opacity: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ranking.promoted,
  .ranking.promoted .badge {
    animation: none;
  }
}

.backdrop {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: grid;
  place-items: center;
  padding: 16px;
  background: rgba(5, 5, 6, 0.72);
  animation: fade 0.2s ease-out;
}

.dialog {
  width: min(520px, 100%);
  animation: rise 0.35s cubic-bezier(0.2, 0.8, 0.2, 1);
}

.verdict {
  margin: 0;
  font-size: 4.2rem;
  font-weight: 600;
  line-height: 0.95;
  letter-spacing: -0.05em;
}

.verdict.lost {
  color: var(--text-muted);
}

.how {
  margin: 10px 0 26px;
  font-size: 0.8rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.facts {
  display: grid;
  margin: -12px 0 22px;
  border-top: 1px solid var(--line);
}

.facts > div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
  font-size: 0.88rem;
}

.facts dt {
  color: var(--text-muted);
}

.facts dd {
  margin: 0;
  text-align: right;
}

.note {
  margin: -10px 0 22px;
  font-size: 0.85rem;
  color: var(--text-muted);
}

.versus {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 12px;
  margin-bottom: 26px;
}

.side {
  display: grid;
  gap: 6px;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 3px;
  background: #0c0c0e;
}

.side.right {
  text-align: right;
}

.side.winner {
  border-color: var(--text);
}

.side strong {
  font-size: 1.05rem;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.who {
  font-size: 0.8rem;
  color: var(--text-muted);
}

.vs {
  font-size: 0.75rem;
  color: var(--text-faint);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 12px;
}

.actions .btn-secondary {
  padding: 12px 18px;
}

.cta.small {
  gap: 18px;
  padding: 12px 14px 12px 20px;
  font-size: 1rem;
  box-shadow: none;
}

@keyframes fade {
  from {
    opacity: 0;
  }
}

@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .backdrop,
  .dialog {
    animation: none;
  }
}
</style>
