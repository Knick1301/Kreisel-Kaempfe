<script setup lang="ts">
import { ref } from 'vue'

withDefaults(defineProps<{ size?: number }>(), { size: 44 })

const LOGO_URL = '/logokreisel.png'
const missing = ref(false)
const spinning = ref(true)

function spin() {
  if (spinning.value) return
  spinning.value = true
}
</script>

<template>
  <span class="logo-mark" :style="{ width: `${size}px`, height: `${size}px` }" @mouseenter="spin">
    <img
      v-if="!missing"
      class="mark"
      :class="{ spinning }"
      :src="LOGO_URL"
      alt=""
      draggable="false"
      @animationend="spinning = false"
      @error="missing = true"
    />
    <svg
      v-else
      class="mark"
      :class="{ spinning }"
      viewBox="0 0 32 32"
      aria-hidden="true"
      @animationend="spinning = false"
    >
      <circle
        cx="16"
        cy="16"
        r="12.5"
        fill="none"
        stroke="currentColor"
        stroke-width="2.5"
        stroke-dasharray="16.5 3.1"
      />
      <circle cx="16" cy="16" r="4" fill="currentColor" />
    </svg>
  </span>
</template>

<style scoped>
.logo-mark {
  display: inline-grid;
  place-items: center;
  flex-shrink: 0;
}

.mark {
  width: 100%;
  height: 100%;
  object-fit: contain;
  mix-blend-mode: screen;
}

.mark.spinning {
  animation: spin 2.4s cubic-bezier(0.25, 0.1, 0.25, 1);
}

@keyframes spin {
  from {
    transform: rotate(-360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .mark.spinning {
    animation: none;
  }
}
</style>
