<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { FightMode, Friend } from '@/api'
import LeagueBadge from '@/components/LeagueBadge.vue'
import {
  acceptFriendRequest,
  friends,
  refreshFriends,
  removeFriendship,
  rejectChallenge,
  sendChallenge,
  sendFriendRequest,
} from '@/stores/friends'

/** Freunde im Profil: hinzufügen, Anfragen beantworten, herausfordern. */
const name = ref('')
const busy = ref(false)
const message = ref<{ text: string; error: boolean } | null>(null)

onMounted(() => void refreshFriends())

async function run(action: () => Promise<unknown>, success?: string) {
  busy.value = true
  message.value = null
  try {
    await action()
    if (success) message.value = { text: success, error: false }
  } catch (e) {
    message.value = { text: e instanceof Error ? e.message : String(e), error: true }
  } finally {
    busy.value = false
  }
}

function add() {
  const n = name.value.trim()
  if (!n) return
  void run(async () => {
    await sendFriendRequest(n)
    name.value = ''
  }, `Anfrage an ${n} gesendet.`)
}

function challenge(f: Friend, mode: FightMode) {
  void run(
    () => sendChallenge(f.username, mode),
    `${f.username} ist herausgefordert. Sobald angenommen wird, geht es los.`,
  )
}

/** offene eigene Herausforderung an diesen Freund */
const waitingFor = (f: Friend) =>
  friends.data?.challenges.find((c) => c.mine && !c.matchId && c.to === f.username) ?? null
</script>

<template>
  <section class="panel friends" aria-labelledby="friends-title">
    <header class="panel-head">
      <span id="friends-title" class="label"><i class="dot" />Freunde</span>
      <span v-if="friends.data" class="label">{{ friends.data.friends.length }}</span>
    </header>
    <div class="panel-body">
      <form class="add" @submit.prevent="add">
        <label class="sr-only" for="friend-name">Spielername</label>
        <input
          id="friend-name"
          v-model="name"
          type="text"
          placeholder="Spielername"
          autocomplete="off"
          maxlength="20"
        />
        <button type="submit" class="btn-secondary" :disabled="busy || !name.trim()">
          Hinzufügen
        </button>
      </form>
      <p v-if="message" class="msg" :class="{ error: message.error }" role="status">
        {{ message.text }}
      </p>

      <template v-if="friends.data">
        <div v-if="friends.data.incoming.length" class="group">
          <h3 class="label">Anfragen an dich</h3>
          <ul>
            <li v-for="f in friends.data.incoming" :key="f.id" class="row">
              <span class="who">{{ f.username }}</span>
              <span class="actions">
                <button
                  type="button"
                  class="btn-secondary"
                  :disabled="busy"
                  @click="run(() => removeFriendship(f.id))"
                >
                  Ablehnen
                </button>
                <button
                  type="button"
                  class="btn-secondary strong"
                  :disabled="busy"
                  @click="
                    run(() => acceptFriendRequest(f.id), `${f.username} ist jetzt dein Freund.`)
                  "
                >
                  Annehmen
                </button>
              </span>
            </li>
          </ul>
        </div>

        <ul v-if="friends.data.friends.length" class="list">
          <li v-for="f in friends.data.friends" :key="f.id" class="row friend">
            <span class="who">
              <i
                class="online"
                :class="{ on: f.online }"
                :title="f.online ? 'online' : 'offline'"
              />
              {{ f.username }}
            </span>
            <span class="ranks">
              <span class="rank" :title="`Klassisch: ${f.ranking.league}`">
                <LeagueBadge :tier="f.ranking.tier" :division="f.ranking.division" :size="20" />
                <small>Klassisch</small>
              </span>
              <span
                class="rank"
                :title="
                  f.arenaRanking ? `Arena: ${f.arenaRanking.league}` : 'Noch kein Arena-Kampf'
                "
              >
                <LeagueBadge
                  v-if="f.arenaRanking"
                  :tier="f.arenaRanking.tier"
                  :division="f.arenaRanking.division"
                  :size="20"
                />
                <i v-else class="no-rank">–</i>
                <small>Arena</small>
              </span>
            </span>
            <span class="actions">
              <template v-if="waitingFor(f)">
                <span class="waiting">Wartet auf Antwort …</span>
                <button
                  type="button"
                  class="btn-secondary"
                  :disabled="busy"
                  @click="run(() => rejectChallenge(waitingFor(f)!.id))"
                >
                  Zurückziehen
                </button>
              </template>
              <template v-else>
                <button
                  type="button"
                  class="btn-secondary"
                  :disabled="busy"
                  title="Klassischer Kampf: anspinnen, Ult selbst auslösen"
                  @click="challenge(f, 'CLASSIC')"
                >
                  Herausfordern
                </button>
                <button
                  type="button"
                  class="btn-secondary"
                  :disabled="busy"
                  title="Arena-Modus: selbst steuern mit WASD"
                  @click="challenge(f, 'ARENA')"
                >
                  Arena
                </button>
              </template>
              <button
                type="button"
                class="icon"
                :disabled="busy"
                :aria-label="`${f.username} entfernen`"
                title="Entfernen"
                @click="run(() => removeFriendship(f.id))"
              >
                ×
              </button>
            </span>
          </li>
        </ul>
        <p v-else class="hint">Noch keine Freunde. Füge jemanden über den Spielernamen hinzu.</p>

        <div v-if="friends.data.outgoing.length" class="group">
          <h3 class="label">Gesendet</h3>
          <ul>
            <li v-for="f in friends.data.outgoing" :key="f.id" class="row">
              <span class="who faint">{{ f.username }}</span>
              <button
                type="button"
                class="btn-secondary"
                :disabled="busy"
                @click="run(() => removeFriendship(f.id))"
              >
                Zurückziehen
              </button>
            </li>
          </ul>
        </div>
      </template>
      <p v-else class="hint">Lade Freunde …</p>
    </div>
  </section>
</template>

<style scoped>
.friends .panel-body {
  display: grid;
  gap: 14px;
}

.add {
  display: flex;
  gap: 8px;
}

.add input {
  flex: 1;
  min-width: 0;
  padding: 8px 10px;
  border: 1px solid var(--line-strong);
  border-radius: 3px;
  background: none;
  color: var(--text);
  font: inherit;
}

.msg {
  margin: 0;
  font-size: 0.82rem;
  color: var(--text-muted);
}

.msg.error {
  color: #ef6a5b;
}

.group h3 {
  margin: 0 0 6px;
}

ul {
  display: grid;
  margin: 0;
  padding: 0;
  list-style: none;
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
}

.friend {
  flex-wrap: wrap;
}

.who {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
  overflow-wrap: anywhere;
}

.online {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--line-strong);
}

.online.on {
  background: #6fd08c;
}

.ranks {
  display: flex;
  gap: 12px;
}

.rank {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.rank small {
  font-size: 0.7rem;
  color: var(--text-faint);
}

.no-rank {
  width: 20px;
  font-style: normal;
  text-align: center;
  color: var(--text-faint);
}

.actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.actions .btn-secondary {
  padding: 6px 10px;
  font-size: 0.8rem;
}

.btn-secondary.strong {
  border-color: var(--text);
  color: var(--text);
}

.waiting {
  font-size: 0.78rem;
  color: var(--text-muted);
}

.icon {
  width: 28px;
  height: 28px;
  border: 0;
  background: none;
  color: var(--text-faint);
  font-size: 1.1rem;
  cursor: pointer;
}

.icon:hover {
  color: var(--text);
}

.hint {
  margin: 0;
  font-size: 0.85rem;
  color: var(--text-muted);
}

.faint {
  color: var(--text-muted);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}
</style>
