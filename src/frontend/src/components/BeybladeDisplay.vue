<script setup lang="ts">
import { defineAsyncComponent, ref } from 'vue'
import type { PartDto } from '@/api'
import { useModelUrls } from '@/composables/useModelUrls'
import BeybladeSpinner from './BeybladeSpinner.vue'

const BeybladeModel = defineAsyncComponent(() => import('./three/BeybladeModel.vue'))

const props = defineProps<{ parts: PartDto[] }>()

const urls = useModelUrls(() => props.parts)
const spinKey = ref(0)
</script>

<template>
  <div v-if="urls" class="model" @mouseenter="spinKey++">
    <BeybladeModel :urls="urls" :spin-key="spinKey" />
  </div>
  <BeybladeSpinner v-else />
</template>

<style scoped>
.model {
  aspect-ratio: 1;
  width: 100%;
}
</style>
