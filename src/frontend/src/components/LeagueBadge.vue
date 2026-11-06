<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{ tier: string; division?: number | null; size?: number }>(),
  {
    division: null,
    size: 28,
  },
)

const TIERS = ['Bronze', 'Silber', 'Gold', 'Platin', 'Diamant', 'Meister']
const level = computed(() => Math.max(0, TIERS.indexOf(props.tier)))
const pips = computed(() => (props.division ? 4 - props.division : 0))

const HEX = 'M16 2 L28 9 L28 23 L16 30 L4 23 L4 9 Z'
const GEM = 'M16 3 L27 13 L16 29 L5 13 Z'
const INNER_HEX = 'M16 8 L23 12 L23 20 L16 24 L9 20 L9 12 Z'
</script>

<template>
  <svg
    class="league-badge"
    :class="`tier-${level}`"
    viewBox="0 0 32 32"
    :width="size"
    :height="size"
    role="img"
    :aria-label="division ? `${tier} ${['', 'I', 'II', 'III'][division]}` : tier"
  >
    <path v-if="level < 4" :d="HEX" class="outline" />
    <path v-else :d="GEM" class="outline" />

    <path v-if="level === 1" :d="INNER_HEX" class="inner" />
    <path v-if="level === 2 || level === 3" :d="INNER_HEX" class="fill" />
    <path v-if="level === 3" d="M16 11 L20 16 L16 21 L12 16 Z" class="cut" />
    <path v-if="level === 4" d="M16 8 L22 13 L16 23 L10 13 Z" class="fill" />
    <g v-if="level === 5">
      <path :d="GEM" class="fill" />
      <path d="M10 13 L13 9 L16 12 L19 9 L22 13 L16 21 Z" class="cut" />
    </g>

    <g v-if="pips">
      <rect
        v-for="i in pips"
        :key="i"
        :x="16 - (pips * 4 - 1) / 2 + (i - 1) * 4"
        y="33.5"
        width="2.6"
        height="2.6"
        class="pip"
      />
    </g>
  </svg>
</template>

<style scoped>
.league-badge {
  flex-shrink: 0;
  overflow: visible;
}

.outline {
  fill: none;
  stroke: currentColor;
  stroke-width: 1.6;
  stroke-linejoin: round;
}

.inner {
  fill: none;
  stroke: currentColor;
  stroke-width: 1.2;
  opacity: 0.6;
}

.fill {
  fill: currentColor;
}

.cut {
  fill: var(--bg);
}

.pip {
  fill: currentColor;
}

.tier-0 {
  color: var(--text-muted);
}
</style>
