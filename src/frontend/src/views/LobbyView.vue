<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  createLobby,
  fetchBeyblades,
  fetchLobbies,
  fetchLobby,
  joinLobby,
  leaveLobby,
  startLobby,
  type BeybladeDto,
  type LobbyDto,
} from '@/api'
import { auth } from '@/auth'
import { pickActive } from '@/activeBeyblade'

const me = auth.username

const POLL_MS = 2500

const beyblades = ref<BeybladeDto[]>([])
const beybladeId = ref<number | null>(null)
const openLobbies = ref<LobbyDto[]>([])
const current = ref<LobbyDto | null>(null)
const router = useRouter()
const lobbyName = ref(`${me}s Lobby`)
const code = ref('')
const busy = ref(false)
const error = ref<string | null>(null)
const loadError = ref<string | null>(null)

const isHost = computed(() => current.value?.host === me)

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

async function refresh() {
  try {
    openLobbies.value = await fetchLobbies()
    if (current.value) {
      current.value = await fetchLobby(current.value.id)
    }
    loadError.value = null
  } catch (e) {
    if (current.value && e instanceof Error && 'status' in e && e.status === 404) {
      current.value = null
      error.value = 'Die Lobby wurde geschlossen.'
    } else {
      loadError.value = e instanceof Error ? e.message : String(e)
    }
  }
}

let timer: ReturnType<typeof setInterval> | undefined
onMounted(async () => {
  try {
    beyblades.value = await fetchBeyblades()
    beybladeId.value = pickActive(beyblades.value)?.id ?? null
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : String(e)
  }
  await refresh()
  timer = setInterval(refresh, POLL_MS)
})
onUnmounted(() => clearInterval(timer))

/** Sobald der Kampf feststeht, sehen beide Spieler ihn in der Arena (Replay per Seed). */
function watchFight(id: number) {
  router.push({ name: 'kampf-replay', params: { id: String(id) } })
}

watch(
  () => current.value?.result,
  (r) => {
    if (r) watchFight(r.id)
  },
)

const create = () =>
  run(async () => {
    if (beybladeId.value == null) throw new Error('Bitte zuerst einen Kreisel bauen.')
    current.value = await createLobby(lobbyName.value, beybladeId.value)
  })

const join = (id: string) =>
  run(async () => {
    if (beybladeId.value == null) throw new Error('Bitte zuerst einen Kreisel bauen.')
    current.value = await joinLobby(id, beybladeId.value)
    code.value = ''
  })

const start = () =>
  run(async () => {
    const result = await startLobby(current.value!.id)
    watchFight(result.id)
  })

const leave = () =>
  run(async () => {
    if (current.value && current.value.status !== 'FINISHED') {
      await leaveLobby(current.value.id)
    }
    current.value = null
    await refresh()
  })

const statusText = computed(() => {
  const l = current.value
  if (!l) return ''
  if (l.status === 'OPEN') return 'Warte auf Mitspieler …'
  if (l.status === 'READY')
    return isHost.value ? 'Bereit – du kannst starten' : 'Warte auf den Host …'
  return 'Kampf beendet'
})
</script>

<template>
  <main class="lobby-page">
    <div class="lobby">
      <header class="page-head">
        <div>
          <span class="label"><i class="dot" />Freundschaftsmatch</span>
          <h1>Lobby</h1>
        </div>
        <label class="pick">
          <span class="label">Dein Kreisel</span>
          <select v-model="beybladeId" :disabled="!!current">
            <option v-for="b in beyblades" :key="b.id" :value="b.id">{{ b.name }}</option>
          </select>
        </label>
      </header>

      <p v-if="loadError" class="notice">Backend nicht erreichbar ({{ loadError }})</p>
      <p v-if="error" class="notice" role="alert">{{ error }}</p>

      <section v-if="current" class="panel room" aria-label="Deine Lobby">
        <header class="panel-head">
          <span class="label"><i class="dot" />{{ current.name }}</span>
          <span class="label">{{ statusText }}</span>
        </header>
        <div class="panel-body room-body">
          <div class="code">
            <span class="label">Lobby-Code</span>
            <strong class="mono">{{ current.id }}</strong>
            <span class="faint">Gib den Code an deinen Gegner weiter.</span>
          </div>

          <div class="slots">
            <div class="slot filled">
              <span class="label">Host</span>
              <strong>{{ current.host }}</strong>
            </div>
            <span class="vs mono">VS</span>
            <div class="slot" :class="{ filled: current.guest, waiting: !current.guest }">
              <span class="label">Gast</span>
              <strong>{{ current.guest ?? 'frei' }}</strong>
            </div>
          </div>

          <div class="room-actions">
            <button type="button" class="btn-secondary" :disabled="busy" @click="leave">
              {{
                current.status === 'FINISHED' ? 'Zurück' : isHost ? 'Lobby schließen' : 'Verlassen'
              }}
            </button>
            <button
              v-if="isHost && current.status !== 'FINISHED'"
              type="button"
              class="cta"
              :disabled="busy || current.status !== 'READY'"
              @click="start"
            >
              <span>Kampf starten</span>
              <span class="cta-arrow" aria-hidden="true">→</span>
            </button>
          </div>
        </div>
      </section>

      <div v-else class="grid">
        <section class="panel" aria-label="Lobby erstellen">
          <header class="panel-head">
            <span class="label"><i class="dot" />Neue Lobby</span>
          </header>
          <form class="panel-body form" @submit.prevent="create">
            <label class="field">
              <span class="label">Name</span>
              <input v-model="lobbyName" type="text" maxlength="30" spellcheck="false" />
            </label>
            <button type="submit" class="cta" :disabled="busy || beybladeId == null">
              <span>Lobby erstellen</span>
              <span class="cta-arrow" aria-hidden="true">+</span>
            </button>

            <div class="divider"><span class="label">oder per Code</span></div>

            <div class="code-join">
              <input
                v-model="code"
                class="mono"
                type="text"
                maxlength="6"
                placeholder="ABC123"
                spellcheck="false"
                aria-label="Lobby-Code"
              />
              <button
                type="button"
                class="btn-secondary"
                :disabled="busy || code.trim().length < 6"
                @click="join(code.trim())"
              >
                Beitreten
              </button>
            </div>
          </form>
        </section>

        <section class="panel" aria-label="Offene Lobbys">
          <header class="panel-head">
            <span class="label"><i class="dot" />Offene Lobbys</span>
            <span class="label">{{ openLobbies.length }}</span>
          </header>
          <div class="panel-body list">
            <p v-if="openLobbies.length === 0" class="empty">
              Gerade keine offenen Lobbys. Erstelle eine und lade jemanden ein.
            </p>
            <div v-for="l in openLobbies" :key="l.id" class="row">
              <div class="row-text">
                <strong>{{ l.name }}</strong>
                <span class="mono faint">{{ l.id }} · Host {{ l.host }}</span>
              </div>
              <button
                type="button"
                class="btn-secondary"
                :disabled="busy || l.host === me"
                @click="join(l.id)"
              >
                {{ l.host === me ? 'Deine' : 'Beitreten' }}
              </button>
            </div>
          </div>
        </section>
      </div>
    </div>
  </main>
</template>

<style scoped>
.lobby {
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

.page-head h1 {
  margin: 10px 0 0;
  font-size: clamp(3rem, 7vw, 5rem);
  font-weight: 600;
  line-height: 0.95;
  letter-spacing: -0.05em;
}

.pick {
  display: grid;
  gap: 8px;
  min-width: 220px;
}

select,
input {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid var(--line-strong);
  border-radius: 3px;
  background: #0c0c0e;
  color: var(--text);
  font: inherit;
  font-weight: 500;
}

select:focus,
input:focus {
  outline: none;
  border-color: var(--text-muted);
}

.notice {
  margin: 0 0 16px;
  padding: 12px 16px;
  border: 1px solid var(--line-strong);
  border-radius: 3px;
  color: var(--text);
}

.grid {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  gap: 14px;
  align-items: start;
}

.form {
  display: grid;
  gap: 16px;
}

.field {
  display: grid;
  gap: 8px;
}

.form .cta {
  justify-content: space-between;
  box-shadow: none;
}

.divider {
  display: flex;
  align-items: center;
  gap: 12px;
}

.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--line);
}

.code-join {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 10px;
}

.code-join input {
  text-transform: uppercase;
  letter-spacing: 0.2em;
}

.list {
  display: grid;
  gap: 8px;
  padding: 14px;
}

.empty {
  margin: 0;
  padding: 10px;
  color: var(--text-muted);
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid var(--line);
  border-radius: 3px;
  background: #0c0c0e;
}

.row-text {
  display: grid;
  gap: 4px;
}

.row-text strong {
  font-size: 1.05rem;
  font-weight: 600;
}

.faint {
  font-size: 0.8rem;
}

.room-body {
  display: grid;
  gap: 28px;
}

.code {
  display: grid;
  gap: 8px;
}

.code strong {
  font-size: clamp(2.6rem, 8vw, 4.5rem);
  font-weight: 500;
  line-height: 1;
  letter-spacing: 0.12em;
}

.slots {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 14px;
}

.slot {
  display: grid;
  gap: 6px;
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: 3px;
  background: #0c0c0e;
}

.slot.filled {
  border-color: var(--text-muted);
}

.slot strong {
  font-size: 1.3rem;
  font-weight: 600;
}

.slot.waiting strong {
  color: var(--text-faint);
  animation: pulse 1.6s ease-in-out infinite;
}

.vs {
  font-size: 0.8rem;
  color: var(--text-faint);
}

.room-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.room-actions .btn-secondary {
  padding: 14px 20px;
}

@keyframes pulse {
  50% {
    opacity: 0.4;
  }
}

@media (max-width: 760px) {
  .lobby {
    padding: 32px 16px 48px;
  }

  .page-head,
  .grid {
    display: grid;
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .slot.waiting strong {
    animation: none;
  }
}
</style>
