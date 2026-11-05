<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  FINISH_LABEL,
  fetchBattles,
  fetchStats,
  logout,
  type BattleReport,
  type PlayerStats,
} from '@/api'
import { auth, clearSession, initials } from '@/auth'

const router = useRouter()
const me = auth.username
const stats = ref<PlayerStats | null>(null)
const history = ref<BattleReport[]>([])
const loggingOut = ref(false)

onMounted(async () => {
  try {
    ;[stats.value, history.value] = await Promise.all([fetchStats(), fetchBattles(8)])
  } catch {}
})

async function signOut() {
  loggingOut.value = true
  try {
    await logout()
  } catch {}
  clearSession()
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
              <span class="result mono">{{ b.winner === me ? 'S' : 'N' }}</span>
              <span class="opp">
                vs. {{ b.winner === me ? b.loser : b.winner }}
                <small>{{ b.winner === me ? b.loserBeyblade : b.winnerBeyblade }}</small>
              </span>
              <span class="mono faint">{{ FINISH_LABEL[b.finish] }} · R{{ b.rounds }}</span>
            </li>
          </ul>
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

.history {
  display: grid;
  margin: 0;
  padding: 0;
  list-style: none;
}

.history li {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 14px;
  padding: 12px 0;
  border-bottom: 1px solid var(--line);
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
