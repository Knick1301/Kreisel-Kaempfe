<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { FINISH_LABEL, fetchBattles, logout, type BattleReport } from '@/api'
import { clearMe, me as meStore, refreshMe } from '@/stores/me'
import LeagueBadge from '@/components/LeagueBadge.vue'
import { auth, clearSession, initials } from '@/auth'
import { formatClock } from '@/game/playback'

const router = useRouter()
const me = auth.username
const stats = computed(() => meStore.stats)
const rank = computed(() => meStore.rank)
const xpProgress = computed(() => {
  const r = rank.value
  return r
    ? Math.min(100, (100 * (r.xp - r.xpLevelStart)) / Math.max(1, r.xpNextLevel - r.xpLevelStart))
    : 0
})
const history = ref<BattleReport[]>([])

/** Liga-Punkte-Änderung aus Sicht des angemeldeten Spielers */
function myDelta(b: BattleReport): number | null {
  const d = b.winner === me ? b.ratingDeltaWinner : b.ratingDeltaLoser
  return d ?? null
}
const signed = (n: number) => (n > 0 ? `+${n}` : n < 0 ? `−${Math.abs(n)}` : '±0')
const loggingOut = ref(false)

onMounted(async () => {
  try {
    ;[, history.value] = await Promise.all([refreshMe(), fetchBattles(8)])
  } catch {}
})

async function signOut() {
  loggingOut.value = true
  try {
    await logout()
  } catch {}
  clearSession()
  clearMe()
  await router.replace({ name: 'anmelden', query: { mode: 'login' } })
}
</script>

<template>
  <main class="profile">
    <header class="page-head">
      <div class="who">
        <span class="avatar">{{ initials }}</span>
        <div>
          <span class="label"><i class="dot" />Profil</span>
          <h1>{{ me }}</h1>
        </div>
      </div>
      <dl class="numbers">
        <div>
          <dt class="label">Siege</dt>
          <dd class="mono">{{ stats?.wins ?? '–' }}</dd>
        </div>
        <div>
          <dt class="label">Niederlagen</dt>
          <dd class="mono">{{ stats?.losses ?? '–' }}</dd>
        </div>
        <div>
          <dt class="label">Rang</dt>
          <dd class="mono">{{ stats?.rank ? `#${stats.rank}` : '–' }}</dd>
        </div>
        <div>
          <dt class="label">Gegen KI</dt>
          <dd class="mono">{{ stats ? `${stats.botWins ?? 0}:${stats.botLosses ?? 0}` : '–' }}</dd>
        </div>
      </dl>
    </header>

    <div class="grid">
      <section class="panel" aria-label="Letzte Kämpfe">
        <header class="panel-head">
          <span class="label"><i class="dot" />Letzte Kämpfe</span>
        </header>
        <div class="panel-body">
          <div v-if="history.length === 0" class="hint">
            <p>Noch keine Kämpfe.</p>
            <RouterLink to="/" class="btn-secondary">Zum Schnellen Match <span>→</span></RouterLink>
          </div>
          <ul v-else class="history">
            <li v-for="b in history" :key="b.id" :class="{ won: b.winner === me }">
              <RouterLink
                :to="{ name: 'kampf-replay', params: { id: String(b.id) } }"
                class="entry"
                :aria-label="`Kampf #${b.id} ansehen`"
              >
                <span class="result mono">{{ b.winner === me ? 'S' : 'N' }}</span>
                <span class="opp">
                  vs. {{ b.winner === me ? b.loser : b.winner }}
                  <small>{{ b.winner === me ? b.loserBeyblade : b.winnerBeyblade }}</small>
                </span>
                <span class="mono faint">
                  {{ FINISH_LABEL[b.finish] }} ·
                  {{ b.durationMs != null ? formatClock(b.durationMs) : `R${b.rounds}` }}
                </span>
                <span
                  v-if="myDelta(b) !== null"
                  class="delta mono"
                  :class="{ up: myDelta(b)! > 0 }"
                >
                  {{ signed(myDelta(b)!) }}
                </span>
              </RouterLink>
            </li>
          </ul>
        </div>
      </section>

      <div class="side">
        <section class="panel" aria-label="Liga und Level">
          <header class="panel-head">
            <span class="label"><i class="dot" />Liga</span>
            <span v-if="rank" class="label">{{ rank.rating }} LP</span>
          </header>
          <div class="panel-body league">
            <template v-if="rank">
              <span class="league-title">
                <LeagueBadge :tier="rank.tier" :division="rank.division" :size="44" />
                <strong>{{ rank.league }}</strong>
              </span>
              <div class="bar"><span :style="{ width: `${rank.progress}%` }" /></div>
              <span class="sub">
                <template v-if="rank.nextLeague">
                  Noch {{ rank.pointsToNext }} Punkte bis {{ rank.nextLeague }}
                </template>
                <template v-else>Höchste Liga erreicht</template>
              </span>

              <div class="level-row">
                <span class="level-no mono">{{ rank.level }}</span>
                <div class="level-text">
                  <span>Level {{ rank.level }}</span>
                  <div class="bar thin"><span :style="{ width: `${xpProgress}%` }" /></div>
                  <span class="sub mono">{{ rank.xp }} / {{ rank.xpNextLevel }} XP</span>
                </div>
              </div>
            </template>
            <p v-else class="hint">Lade Liga …</p>
          </div>
        </section>

        <section class="panel" aria-label="Konto">
          <header class="panel-head">
            <span class="label"><i class="dot" />Konto</span>
          </header>
          <div class="panel-body account">
            <dl class="specs">
              <div>
                <dt>Benutzername</dt>
                <dd>{{ me }}</dd>
              </div>
              <div>
                <dt>Sitzung</dt>
                <dd class="mono">gültig 30 Tage</dd>
              </div>
            </dl>
            <button type="button" class="btn-secondary" :disabled="loggingOut" @click="signOut">
              {{ loggingOut ? 'Melde ab …' : 'Abmelden' }}
              <span aria-hidden="true">↩</span>
            </button>
          </div>
        </section>
      </div>
    </div>
  </main>
</template>

<style scoped>
.profile {
  max-width: 1100px;
  margin: 0 auto;
  padding: 48px 32px 72px;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 28px;
}

.who {
  display: flex;
  align-items: center;
  gap: 22px;
}

.avatar {
  display: grid;
  place-items: center;
  width: 88px;
  height: 88px;
  border: 1px solid var(--line-strong);
  border-radius: 50%;
  background: var(--surface-raised);
  font-size: 1.8rem;
  font-weight: 600;
}

h1 {
  margin: 8px 0 0;
  font-size: clamp(2.6rem, 6vw, 4rem);
  font-weight: 600;
  line-height: 0.95;
  letter-spacing: -0.05em;
  overflow-wrap: anywhere;
}

.numbers {
  display: flex;
  gap: 32px;
  margin: 0;
}

.numbers div {
  display: grid;
  gap: 6px;
}

.numbers dd {
  margin: 0;
  font-size: 2rem;
}

.grid {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.side {
  display: grid;
  gap: 14px;
  align-content: start;
}

.league {
  display: grid;
  gap: 12px;
}

.league-title {
  display: flex;
  align-items: center;
  gap: 16px;
}

.league-title strong {
  font-size: 1.8rem;
  font-weight: 600;
  letter-spacing: -0.03em;
}

.bar {
  height: 6px;
  background: var(--line);
}

.bar.thin {
  height: 3px;
}

.bar span {
  display: block;
  height: 100%;
  background: var(--text);
}

.sub {
  font-size: 0.82rem;
  color: var(--text-muted);
}

.level-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 8px;
  padding-top: 16px;
  border-top: 1px solid var(--line);
}

.level-no {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border: 1px solid var(--line-strong);
  border-radius: 50%;
  font-size: 1.1rem;
}

.level-text {
  display: grid;
  flex: 1;
  gap: 6px;
}

.delta {
  min-width: 42px;
  font-size: 0.78rem;
  text-align: right;
  color: var(--text-faint);
}

.delta.up {
  color: var(--text);
}

.history {
  display: grid;
  margin: 0;
  padding: 0;
  list-style: none;
}

.history li {
  border-bottom: 1px solid var(--line);
}

.entry {
  display: grid;
  grid-template-columns: auto 1fr auto auto;
  align-items: center;
  gap: 14px;
  padding: 12px 0;
  transition: background-color 0.15s;
}

.entry:hover {
  background: var(--surface-raised);
}

.result {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: 1px solid var(--line-strong);
  border-radius: 3px;
  font-size: 0.8rem;
  color: var(--text-muted);
}

.won .result {
  border-color: var(--text);
  background: var(--text);
  color: var(--bg);
}

.opp {
  display: grid;
  font-weight: 500;
}

.opp small {
  font-size: 0.78rem;
  color: var(--text-muted);
}

.faint {
  font-size: 0.75rem;
}

.hint {
  display: grid;
  justify-items: start;
  gap: 12px;
  color: var(--text-muted);
}

.hint p {
  margin: 0;
}

.hint .btn-secondary {
  gap: 24px;
}

.account .specs {
  margin-bottom: 20px;
}

.account .btn-secondary {
  width: 100%;
}

@media (max-width: 860px) {
  .profile {
    padding: 32px 16px 48px;
  }

  .page-head,
  .grid {
    display: grid;
    grid-template-columns: 1fr;
  }
}
</style>
