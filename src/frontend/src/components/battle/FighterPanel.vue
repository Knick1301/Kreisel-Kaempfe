<script setup lang="ts">
import { computed } from 'vue'
import { ARCHETYPE_LABEL, type FighterDto } from '@/api'
import StatBar from '@/components/StatBar.vue'

/** Kämpfer-Panel: im Intro mit Werten, im Kampf als HUD mit Spin-Energie. */
const props = withDefaults(
  defineProps<{
    label: string
    fighter?: FighterDto
    view: 'intro' | 'hud'
    energy?: number
    hits?: number
    winner?: boolean | null
    /** Platzhaltername, solange der Gegner gezogen wird */
    rolling?: string | null
  }>(),
  { fighter: undefined, energy: 1, hits: 0, winner: null, rolling: null },
)

const name = computed(() => props.rolling ?? props.fighter?.beyblade ?? '? ? ?')
const owner = computed(() => (props.fighter?.bot ? 'KI-Gegner' : props.fighter?.username))
const stats = computed(() => {
  const s = props.fighter?.stats
  if (!s) return []
  return [
    { label: 'Angriff', value: s.attack },
    { label: 'Verteidigung', value: s.defense },
    { label: 'Ausdauer', value: s.stamina },
    { label: 'Tempo', value: s.dash },
  ]
})
const percent = computed(() => Math.round(Math.max(0, Math.min(1, props.energy)) * 100))
</script>

<template>
  <section class="panel fighter" :class="[view, { won: winner === true, lost: winner === false }]">
    <header class="panel-head">
      <span class="label"><i class="dot" />{{ label }}</span>
      <span v-if="fighter && !rolling" class="label chip">{{
        ARCHETYPE_LABEL[fighter.archetype]
      }}</span>
    </header>

    <div class="panel-body">
      <h2 class="name" :class="{ rolling: !!rolling }">{{ name }}</h2>
      <p v-if="owner && !rolling" class="owner">{{ owner }}</p>

      <template v-if="view === 'intro' && fighter && !rolling">
        <div class="stats">
          <StatBar v-for="s in stats" :key="s.label" :label="s.label" :value="s.value" />
        </div>
        <dl class="specs">
          <div>
            <dt>Gewicht</dt>
            <dd class="mono">{{ fighter.stats.weight }}g</dd>
          </div>
          <div>
            <dt>Burst-Resistenz</dt>
            <dd class="mono">{{ fighter.stats.burstResistance }}</dd>
          </div>
        </dl>
      </template>

      <div v-else-if="view === 'hud'" class="hud">
        <div class="energy-row">
          <span class="label">Spin</span>
          <span class="mono">{{ percent }} %</span>
        </div>
        <div
          class="energy"
          role="meter"
          :aria-label="`Spin-Energie ${name}`"
          :aria-valuenow="percent"
          aria-valuemin="0"
          aria-valuemax="100"
        >
          <span :style="{ transform: `scaleX(${percent / 100})` }" />
        </div>
        <p class="hits mono">{{ hits }} Treffer</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.fighter.won {
  border-color: var(--text-muted);
}

.fighter.lost .name {
  color: var(--text-muted);
}

.chip {
  padding: 2px 6px;
  border: 1px solid var(--line-strong);
  border-radius: 2px;
  color: var(--text-muted);
}

.name {
  margin: 0;
  font-size: 2rem;
  font-weight: 600;
  line-height: 1.05;
  letter-spacing: -0.03em;
  overflow-wrap: anywhere;
}

.name.rolling {
  color: var(--text-muted);
}

.owner {
  margin: 6px 0 20px;
  font-size: 0.85rem;
  color: var(--text-muted);
}

.hud .name {
  font-size: 1.35rem;
}

.hud {
  display: grid;
  gap: 8px;
}

.hud .owner {
  margin-bottom: 4px;
}

.energy-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  font-size: 0.9rem;
}

.energy {
  height: 8px;
  background: var(--line);
  overflow: hidden;
}

.energy span {
  display: block;
  height: 100%;
  background: var(--text);
  transform-origin: left;
}

.hits {
  margin: 0;
  font-size: 0.75rem;
  color: var(--text-faint);
}

.specs {
  margin-bottom: 0;
}
</style>
