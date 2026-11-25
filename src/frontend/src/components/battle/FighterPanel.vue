<script setup lang="ts">
import { computed } from 'vue'
import { ARCHETYPE_LABEL, ULT_HINT, ULT_LABEL, type FighterDto, type UltType } from '@/api'
import StatBar from '@/components/StatBar.vue'
import { ULT_COLOR } from '@/game/ult'

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
    /** Ult-Leiste 0–1, null = Kampf ohne Ults (dann keine Leiste) */
    ult?: number | null
    /** gerade laufende Ult */
    ultActive?: UltType | null
    /** Spin vor dem letzten Treffer (roter Rest), null = nichts nachzuzeigen */
    ghost?: number | null
  }>(),
  {
    fighter: undefined,
    energy: 1,
    hits: 0,
    winner: null,
    rolling: null,
    ult: null,
    ultActive: null,
    ghost: null,
  },
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
const ghostScale = computed(() => (props.ghost == null ? 0 : Math.max(0, Math.min(1, props.ghost))))
const ultType = computed(() => props.fighter?.ult?.type ?? null)
const ultPercent = computed(() =>
  props.ult == null ? null : Math.round(Math.max(0, Math.min(1, props.ult)) * 100),
)
const ultStyle = computed(() => ({
  '--ult': ultType.value ? ULT_COLOR[ultType.value] : 'var(--text)',
}))
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
        <p v-if="ultType" class="ult-info" :style="ultStyle">
          <span class="label">Ult</span>
          <strong>{{ ULT_LABEL[ultType] }}</strong>
          <span class="ult-hint">{{ ULT_HINT[ultType] }}</span>
        </p>
      </template>

      <div v-else-if="view === 'hud'" class="hud">
        <div class="energy-row">
          <span class="label">Spin</span>
          <span class="mono">{{ percent }} %</span>
        </div>
        <div
          class="energy"
          :class="{ hit: ghostScale > 0 }"
          role="meter"
          :aria-label="`Spin-Energie ${name}`"
          :aria-valuenow="percent"
          aria-valuemin="0"
          aria-valuemax="100"
        >
          <span
            v-if="ghostScale > 0"
            class="ghost"
            :style="{ transform: `scaleX(${ghostScale})` }"
          />
          <span class="fill" :style="{ transform: `scaleX(${percent / 100})` }" />
        </div>
        <template v-if="ultType && ultPercent !== null">
          <div class="energy-row ult-row" :style="ultStyle">
            <span class="label">{{ ULT_LABEL[ultType] }}</span>
            <span class="mono">{{
              ultActive ? 'Aktiv!' : ultPercent >= 100 ? 'Bereit' : `${ultPercent} %`
            }}</span>
          </div>
          <div
            class="ult-meter"
            :class="{ active: !!ultActive, full: ultPercent >= 100 }"
            :style="ultStyle"
            role="meter"
            :aria-label="`Ult-Leiste ${name}`"
            :aria-valuenow="ultPercent"
            aria-valuemin="0"
            aria-valuemax="100"
          >
            <span :style="{ transform: `scaleX(${ultActive ? 1 : ultPercent / 100})` }" />
          </div>
        </template>
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
  position: relative;
  height: 8px;
  background: var(--line);
  overflow: hidden;
}

.energy span {
  position: absolute;
  inset: 0;
  display: block;
  transform-origin: left;
}

.energy .fill {
  background: var(--text);
}

/* Treffer: roter Rest, der nachläuft */
.energy .ghost {
  background: #e5484d;
}

.energy.hit .fill {
  box-shadow: 0 0 6px rgba(229, 72, 77, 0.6);
}

.hits {
  margin: 0;
  font-size: 0.75rem;
  color: var(--text-faint);
}

.specs {
  margin-bottom: 0;
}

.ult-info {
  display: grid;
  gap: 2px;
  margin: 14px 0 0;
  padding-left: 8px;
  border-left: 2px solid var(--ult);
  font-size: 0.85rem;
}

.ult-info strong {
  color: var(--ult);
  font-weight: 600;
}

.ult-hint {
  color: var(--text-muted);
  font-size: 0.78rem;
}

.ult-row .label {
  color: var(--ult);
}

.ult-meter {
  height: 5px;
  background: var(--line);
  overflow: hidden;
}

.ult-meter span {
  display: block;
  height: 100%;
  background: var(--ult);
  transform-origin: left;
  transition: transform 0.15s linear;
}

.ult-meter.full span,
.ult-meter.active span {
  box-shadow: 0 0 10px var(--ult);
}

.ult-meter.active {
  animation: ult-pulse 0.5s ease-in-out infinite alternate;
}

@keyframes ult-pulse {
  to {
    filter: brightness(1.8);
  }
}

@media (prefers-reduced-motion: reduce) {
  .ult-meter.active {
    animation: none;
  }

  .ult-meter span {
    transition: none;
  }
}
</style>
