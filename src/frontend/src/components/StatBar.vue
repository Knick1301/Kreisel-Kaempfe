<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{ label: string; value: number; max?: number; delta?: number }>(),
  { max: 20, delta: 0 },
)

const percent = computed(() => Math.min(100, Math.max(0, (props.value / props.max) * 100)))
</script>

<template>
  <div class="stat">
    <span class="stat-label">{{ label }}</span>
    <span class="value">
      <span v-if="delta" class="delta" :class="delta > 0 ? 'up' : 'down'">
        {{ delta > 0 ? '+' : '−' }}{{ Math.abs(delta) }}
      </span>
      {{ value }}<span class="max">/{{ max }}</span>
    </span>
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
  </div>
</template>

<style scoped>
.stat {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: baseline;
  gap: 8px 12px;
}

.stat-label {
  font-size: 0.93rem;
  color: var(--text-muted);
}

.value {
  font-family: var(--font-mono);
  font-size: 1.1rem;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.delta {
  margin-right: 8px;
  padding: 1px 5px;
  border: 1px solid var(--line-strong);
  border-radius: 2px;
  font-size: 0.72rem;
  vertical-align: 2px;
}

.delta.up {
  color: var(--text);
  border-color: var(--text-muted);
}

.delta.down {
  color: var(--text-faint);
}

.max {
  font-size: 0.75rem;
  color: var(--text-faint);
}

.track {
  position: relative;
  grid-column: 1 / -1;
  height: 6px;
  background: var(--line);
}

.track::after {
  content: '';
  position: absolute;
  inset: 0;
  background: repeating-linear-gradient(
    90deg,
    transparent 0 calc(25% - 2px),
    var(--surface) calc(25% - 2px) 25%
  );
}

.fill {
  height: 100%;
  transition: width 0.35s cubic-bezier(0.2, 0.8, 0.2, 1);
  background: var(--text);
  transform-origin: left;
  animation: grow 0.8s cubic-bezier(0.2, 0.8, 0.2, 1) both;
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
