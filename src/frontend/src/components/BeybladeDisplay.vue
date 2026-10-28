<script setup lang="ts">
import { defineAsyncComponent, ref, watchEffect } from 'vue'
import { modelUrl, type PartDto } from '@/api'
import BeybladeSpinner from './BeybladeSpinner.vue'

const BeybladeModel = defineAsyncComponent(() => import('./three/BeybladeModel.vue'))

const props = defineProps<{ parts: PartDto[] }>()

const urls = ref<string[] | null>(null)
const spinKey = ref(0)

async function exists(url: string) {
  try {
    const res = await fetch(url, { method: 'HEAD' })
    return res.ok && !res.headers.get('content-type')?.includes('text/html')
  } catch {
    return false
  }
}

watchEffect(async () => {
  const paths = props.parts.map((p) => p.modelPath)
  if (paths.some((p) => !p)) {
    urls.value = null
    return
  }
  const candidates = paths.map((p) => modelUrl(p!))
  const found = await Promise.all(candidates.map(exists))
  urls.value = found.every(Boolean) ? candidates : null
})
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
