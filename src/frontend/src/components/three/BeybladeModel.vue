<script setup lang="ts">
import { TresCanvas } from '@tresjs/core'
import BeybladeRotor from './BeybladeRotor.vue'
import StudioEnvironment from './StudioEnvironment.vue'

defineProps<{ urls: string[]; spinKey: number }>()
</script>

<template>
  <TresCanvas alpha :clear-alpha="0">
    <TresPerspectiveCamera :position="[0, 1.6, 2.4]" :look-at="[0, 0, 0]" :fov="40" />
    <StudioEnvironment />
    <TresAmbientLight :intensity="0.4" />
    <TresDirectionalLight :position="[3, 5, 2]" :intensity="1.8" />
    <TresDirectionalLight :position="[-3, 2, -2]" :intensity="1" color="#5b9bff" />
    <BeybladeRotor :urls="urls" :spin-key="spinKey" />
  </TresCanvas>
</template>
