<script setup lang="ts">
import { computed } from 'vue'
import { ULT_LABEL, type FighterDto, type UltType } from '@/api'
import { ULT_COLOR } from '@/game/ult'
import BeyEmblem from './BeyEmblem.vue'

/**
 * Ult-Anzeige unten links: Emblem des eigenen Kreisels, darum ein Ring, der sich mit der Ult-Leiste füllt.
 * Voll wartet die Ult auf den Spieler: Der Kreis wird zum Knopf (Klick, Leertaste oder Q), das Emblem
 * dreht sich und der Ring leuchtet. Läuft die Ult, pulsiert er.
 */
const props = withDefaults(
  defineProps<{
    fighter: FighterDto | undefined
    /** Ult-Leiste 0–1 */
    value: number
    /** gerade laufende Ult */
    active?: UltType | null
    /** Spieler kann die volle Ult auslösen (eigener, laufender Kampf) */
    interactive?: boolean
    /** ohne Drehen und Pulsieren */
    calm?: boolean
  }>(),
  { active: null, interactive: false, calm: false },
)
const emit = defineEmits<{ activate: [] }>()

const SIZE = 112
const C = SIZE / 2
const RING_R = 50
const RING_W = 6
const CIRC = 2 * Math.PI * RING_R

const ult = computed(() => props.fighter?.ult?.type ?? null)
const color = computed(() => (ult.value ? ULT_COLOR[ult.value] : 'var(--text)'))
const fill = computed(() => (props.active ? 1 : Math.max(0, Math.min(1, props.value))))
const full = computed(() => !props.active && fill.value >= 1)
const ready = computed(() => full.value && props.interactive)
const percent = computed(() => Math.round(fill.value * 100))

const status = computed(() => {
  if (props.active) return 'Aktiv!'
  if (ready.value) return 'Jetzt drücken!'
  if (full.value) return 'Bereit'
  return `${percent.value} %`
})

function press() {
  if (ready.value) emit('activate')
}
</script>

<template>
  <component
    :is="interactive ? 'button' : 'div'"
    v-if="ult"
    :type="interactive ? 'button' : undefined"
    class="ult-badge"
    :class="{ full, ready, active: !!active, calm }"
    :style="{ '--ult': color }"
    :disabled="interactive && !ready ? true : undefined"
    :aria-label="
      ready ? `${ULT_LABEL[ult]} auslösen (Leertaste oder Q)` : `Ult ${ULT_LABEL[ult]}: ${status}`
    "
    @click="press"
  >
    <svg :width="SIZE" :height="SIZE" :viewBox="`0 0 ${SIZE} ${SIZE}`" aria-hidden="true">
      <circle class="back" :cx="C" :cy="C" :r="RING_R + RING_W / 2 + 3" />
      <circle class="track" :cx="C" :cy="C" :r="RING_R" :stroke-width="RING_W" />
      <circle
        class="progress"
        :cx="C"
        :cy="C"
        :r="RING_R"
        :stroke-width="RING_W"
        :stroke-dasharray="CIRC"
        :stroke-dashoffset="CIRC * (1 - fill)"
        :transform="`rotate(-90 ${C} ${C})`"
      />
      <g :transform="`translate(${C} ${C})`">
        <g class="emblem">
          <BeyEmblem :fighter="fighter" />
        </g>
      </g>
    </svg>
    <span class="caption">
      <span class="name">{{ ULT_LABEL[ult] }}</span>
      <span class="status mono">{{ status }}</span>
      <span v-if="ready" class="keys mono"><kbd>Klick</kbd> <kbd>Leertaste</kbd> <kbd>Q</kbd></span>
    </span>
  </component>
</template>

<style scoped>
.ult-badge {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0;
  border: 0;
  background: none;
  color: inherit;
  font: inherit;
  text-align: left;
  pointer-events: none;
}

button.ult-badge.ready {
  cursor: pointer;
  pointer-events: auto;
}

button.ult-badge:focus-visible {
  outline: 2px solid var(--ult);
  outline-offset: 4px;
  border-radius: 50px;
}

svg {
  flex: none;
  overflow: visible;
}

.back {
  fill: rgba(10, 10, 11, 0.82);
  stroke: var(--line);
}

.track {
  fill: none;
  stroke: var(--line-strong);
}

.progress {
  fill: none;
  stroke: var(--ult);
  stroke-linecap: round;
  transition: stroke-dashoffset 0.2s linear;
}

/* Das Emblem steht still, bis die Ult bereit ist */
.emblem {
  transform-origin: 0 0;
  opacity: 0.75;
  transition: opacity 0.2s;
}

.ult-badge.full .emblem,
.ult-badge.active .emblem {
  opacity: 1;
  animation: spin 0.7s linear infinite;
}

.ult-badge.full .progress,
.ult-badge.active .progress {
  filter: drop-shadow(0 0 6px var(--ult));
}

.ult-badge.ready svg {
  animation: beckon 0.7s ease-in-out infinite alternate;
}

.ult-badge.active svg {
  animation: pulse 0.5s ease-in-out infinite alternate;
}

.caption {
  display: grid;
  gap: 2px;
  padding: 6px 10px;
  border: 1px solid var(--line);
  border-radius: 3px;
  background: rgba(10, 10, 11, 0.82);
}

.ult-badge.ready .caption {
  border-color: var(--ult);
}

.name {
  font-weight: 600;
  color: var(--ult);
}

.status {
  font-size: 0.8rem;
  color: var(--text-muted);
}

.ult-badge.full .status,
.ult-badge.active .status {
  color: var(--text);
}

.keys {
  font-size: 0.7rem;
  color: var(--text-muted);
}

kbd {
  padding: 0 4px;
  border: 1px solid var(--line-strong);
  border-radius: 2px;
  font: inherit;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes beckon {
  from {
    transform: scale(1);
    filter: drop-shadow(0 0 4px var(--ult));
  }
  to {
    transform: scale(1.08);
    filter: drop-shadow(0 0 16px var(--ult));
  }
}

@keyframes pulse {
  to {
    filter: brightness(1.5) drop-shadow(0 0 10px var(--ult));
  }
}

.ult-badge.calm .emblem,
.ult-badge.calm svg {
  animation: none;
}

@media (prefers-reduced-motion: reduce) {
  .emblem,
  .ult-badge svg {
    animation: none !important;
  }

  .progress {
    transition: none;
  }
}
</style>
