<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { fetchBeyblades, type BeybladeDto } from '@/api'
import BeybladeDisplay from '@/components/BeybladeDisplay.vue'
import StatBar from '@/components/StatBar.vue'

const beyblade = ref<BeybladeDto | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

onMounted(async () => {
  try {
    beyblade.value = (await fetchBeyblades())[0] ?? null
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
})

const parts = computed(() =>
  beyblade.value ? [beyblade.value.fusionWheel, beyblade.value.spinTrack, beyblade.value.tip] : [],
)

const stats = computed(() => {
  const s = beyblade.value?.stats
  if (!s) return []
  return [
    { label: 'Angriff', value: s.attack, max: 20 },
    { label: 'Verteidigung', value: s.defense, max: 20 },
    { label: 'Ausdauer', value: s.stamina, max: 20 },
    { label: 'Gewicht', value: s.weight, max: 100 },
  ]
})

const modes = [
  { id: 'quick', label: 'Schnelles Match', icon: 'swords' },
  { id: 'friend', label: 'Freundschafts­match', icon: 'users' },
  { id: 'tournament', label: 'Turnier', icon: 'trophy' },
] as const
</script>

<template>
  <main class="home">
    <section class="card bey-card">
      <header class="card-head">
        <h2>Dein Beyblade</h2>
        <RouterLink to="/baukasten" class="edit-link">
          Bearbeiten
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 20h9" />
            <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z" />
          </svg>
        </RouterLink>
      </header>

      <p v-if="error" class="hint">Backend nicht erreichbar ({{ error }})</p>
      <p v-else-if="loading" class="hint">Lade Beyblade …</p>
      <p v-else-if="!beyblade" class="hint">Du hast noch keinen Beyblade gebaut.</p>

      <div v-else class="bey-body">
        <div class="bey-visual">
          <BeybladeDisplay :parts="parts" />
        </div>

        <div class="bey-info">
          <h3 class="bey-name">{{ beyblade.name }}</h3>
          <div class="stats">
            <StatBar
              v-for="s in stats"
              :key="s.label"
              :label="s.label"
              :value="s.value"
              :max="s.max"
            />
          </div>
        </div>
      </div>
    </section>

    <section class="card modes" aria-label="Spielmodus wählen">
      <button v-for="mode in modes" :key="mode.id" type="button" class="mode">
        <svg
          class="mode-icon"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.5"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <template v-if="mode.icon === 'swords'">
            <polyline points="14.5 17.5 3 6 3 3 6 3 17.5 14.5" />
            <line x1="13" x2="19" y1="19" y2="13" />
            <line x1="16" x2="20" y1="16" y2="20" />
            <line x1="19" x2="21" y1="21" y2="19" />
            <polyline points="14.5 6.5 18 3 21 3 21 6 17.5 9.5" />
            <line x1="5" x2="9" y1="14" y2="18" />
            <line x1="7" x2="4" y1="17" y2="20" />
            <line x1="3" x2="5" y1="19" y2="21" />
          </template>
          <template v-else-if="mode.icon === 'users'">
            <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
            <circle cx="9" cy="7" r="4" />
            <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
            <path d="M16 3.13a4 4 0 0 1 0 7.75" />
          </template>
          <template v-else>
            <path d="M6 9H4.5a2.5 2.5 0 0 1 0-5H6" />
            <path d="M18 9h1.5a2.5 2.5 0 0 0 0-5H18" />
            <path d="M4 22h16" />
            <path d="M10 14.66V17c0 .55-.47.98-.97 1.21C7.85 18.75 7 20.24 7 22" />
            <path d="M14 14.66V17c0 .55.47.98.97 1.21C16.15 18.75 17 20.24 17 22" />
            <path d="M18 2H6v7a6 6 0 0 0 12 0V2Z" />
          </template>
        </svg>
        <span>{{ mode.label }}</span>
      </button>
    </section>
  </main>
</template>

<style scoped>
.home {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(220px, 0.75fr);
  gap: 28px;
  align-items: start;
  max-width: 1040px;
  margin: 0 auto;
  padding: 56px 28px;
}

.card {
  background: linear-gradient(160deg, var(--surface-raised), var(--surface) 60%);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.35);
}

.bey-card {
  padding: 28px 32px 36px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-head h2 {
  margin: 0;
  font-size: 1.3rem;
  font-weight: 700;
}

.edit-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.85rem;
  color: var(--text-muted);
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  transition:
    color 0.2s,
    background-color 0.2s;
}

.edit-link svg {
  width: 14px;
  height: 14px;
}

.edit-link:hover {
  color: var(--text);
  background: rgba(38, 89, 166, 0.25);
}

.bey-body {
  display: grid;
  grid-template-columns: minmax(150px, 220px) 1fr;
  align-items: center;
  gap: 32px;
  margin-top: 20px;
}

.hint {
  margin: 40px 0;
  text-align: center;
  color: var(--text-muted);
}

.bey-name {
  margin: 0 0 22px;
  text-align: center;
  font-size: 1.1rem;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.stats {
  display: grid;
  gap: 12px;
}

.modes {
  display: grid;
  gap: 10px;
  padding: 10px;
}

.mode {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 112px;
  padding: 16px 12px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: linear-gradient(180deg, var(--primary-hover), var(--primary));
  font-size: 1.05rem;
  font-weight: 600;
  text-align: center;
  cursor: pointer;
  overflow: hidden;
  transition:
    transform 0.2s,
    box-shadow 0.2s,
    border-color 0.2s;
}

.mode::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(110deg, transparent 30%, rgba(255, 255, 255, 0.14), transparent 70%);
  transform: translateX(-100%);
  transition: transform 0.6s;
}

.mode:hover {
  transform: translateY(-2px);
  border-color: rgba(120, 170, 255, 0.5);
  box-shadow: 0 10px 24px rgba(37, 99, 235, 0.35);
}

.mode:hover::after {
  transform: translateX(100%);
}

.mode:active {
  transform: translateY(0);
}

.mode-icon {
  width: 40px;
  height: 40px;
  transition: transform 0.3s;
}

.mode:hover .mode-icon {
  transform: scale(1.12) rotate(-6deg);
}

@media (max-width: 820px) {
  .home {
    grid-template-columns: 1fr;
    padding: 28px 16px;
  }

  .modes {
    grid-template-columns: repeat(3, 1fr);
  }

  .mode {
    min-height: 96px;
    font-size: 0.9rem;
  }
}

@media (max-width: 560px) {
  .bey-card {
    padding: 22px 18px 26px;
  }

  .bey-body {
    grid-template-columns: 1fr;
    gap: 20px;
  }

  .bey-visual {
    width: 180px;
    margin: 0 auto;
  }

  .modes {
    grid-template-columns: 1fr;
  }

  .mode {
    flex-direction: row;
    min-height: 64px;
  }

  .mode-icon {
    width: 28px;
    height: 28px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .mode,
  .mode::after,
  .mode-icon {
    transition: none;
  }
}
</style>
