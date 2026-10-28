<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{ label: string; value: number; max?: number }>(), {
  max: 20,
})

const percent = computed(() => Math.min(100, Math.max(0, (props.value / props.max) * 100)))
</script>

<template>
  <div class="stat">
    <span class="label">{{ label }}</span>
    <div
      class="track"
      role="meter"
      :aria-label="label"
      :aria-valuenow="value"
      aria-valuemin="0"
      :aria-valuemax="max"
    >
      <div class="fill" :style="{ width: `${percent}%` }" />
    </div>
    <span class="value">{{ value }}</span>
  </div>
</template>

<style scoped>
.stat {
  display: grid;
  grid-template-columns: 96px 1fr 28px;
  align-items: center;
  gap: 12px;
  font-size: 0.85rem;
}

.label {
  color: var(--text-muted);
}

.track {
  height: 10px;
  border-radius: 999px;
  background: var(--bar-track);
  overflow: hidden;
}

.fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #1d4a8a, var(--accent));
  box-shadow: 0 0 10px var(--accent-soft);
  animation: grow 0.9s cubic-bezier(0.2, 0.8, 0.2, 1) both;
  transform-origin: left;
}

.value {
  text-align: right;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

@keyframes grow {
  from {
    transform: scaleX(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .fill {
    animation: none;
  }
}
</style>
