<script setup lang="ts">
import { shallowRef, watch } from 'vue'
import { useLoop } from '@tresjs/core'
import type { Group } from 'three'

const props = defineProps<{ spinKey: number }>()

const START_SPEED = 9
const FRICTION = 1.1

const rotor = shallowRef<Group>()
let speed = START_SPEED
let time = 0

watch(
  () => props.spinKey,
  () => {
    speed = START_SPEED
  },
)

const { onBeforeRender } = useLoop()

onBeforeRender(({ delta }) => {
  if (!rotor.value) return
  time += delta
  if (speed >= 0.01) {
    rotor.value.rotation.y += speed * delta
    speed *= Math.exp(-FRICTION * delta)
  }
  const tilt = Math.min(speed / START_SPEED, 1) * 0.06
  rotor.value.rotation.x = Math.sin(time * 6) * tilt
  rotor.value.rotation.z = Math.cos(time * 6) * tilt
})

const blades = [0, 1, 2, 3, 4, 5].map((i) => (i * Math.PI) / 3)
</script>

<template>
  <TresGroup ref="rotor">
    <TresMesh :position="[0, -0.42, 0]" :rotation="[Math.PI, 0, 0]">
      <TresConeGeometry :args="[0.22, 0.36, 32]" />
      <TresMeshStandardMaterial color="#20242c" :metalness="0.3" :roughness="0.5" />
    </TresMesh>
    <TresMesh :position="[0, -0.22, 0]">
      <TresCylinderGeometry :args="[0.3, 0.24, 0.1, 32]" />
      <TresMeshStandardMaterial color="#2a3140" :metalness="0.4" :roughness="0.4" />
    </TresMesh>

    <TresMesh :position="[0, -0.12, 0]">
      <TresCylinderGeometry :args="[0.72, 0.68, 0.1, 64]" />
      <TresMeshStandardMaterial color="#c9d2e0" :metalness="1" :roughness="0.25" />
    </TresMesh>

    <TresMesh :position="[0, 0.0, 0]">
      <TresCylinderGeometry :args="[0.58, 0.62, 0.14, 64]" />
      <TresMeshStandardMaterial color="#1c43c0" :metalness="0.5" :roughness="0.35" />
    </TresMesh>
    <TresGroup v-for="a in blades" :key="a" :rotation="[0, a, 0]">
      <TresMesh :position="[0.72, 0, 0.08]" :rotation="[0, -0.5, 0]">
        <TresBoxGeometry :args="[0.42, 0.12, 0.16]" />
        <TresMeshStandardMaterial color="#2b5be0" :metalness="0.5" :roughness="0.3" />
      </TresMesh>
      <TresMesh :position="[0.5, 0.08, 0]">
        <TresBoxGeometry :args="[0.1, 0.04, 0.12]" />
        <TresMeshStandardMaterial color="#e0b040" :metalness="1" :roughness="0.3" />
      </TresMesh>
    </TresGroup>

    <TresMesh :position="[0, 0.1, 0]">
      <TresCylinderGeometry :args="[0.3, 0.34, 0.08, 48]" />
      <TresMeshStandardMaterial color="#d9e0ea" :metalness="1" :roughness="0.2" />
    </TresMesh>
    <TresMesh :position="[0, 0.15, 0]">
      <TresCylinderGeometry :args="[0.22, 0.24, 0.04, 48]" />
      <TresMeshStandardMaterial color="#0a1d63" :metalness="0.3" :roughness="0.3" />
    </TresMesh>
    <TresMesh :position="[0, 0.17, 0]" :rotation="[Math.PI / 2, 0, 0]">
      <TresTorusGeometry :args="[0.23, 0.015, 12, 48]" />
      <TresMeshStandardMaterial color="#e0b040" :metalness="1" :roughness="0.25" />
    </TresMesh>
    <TresMesh :position="[0, 0.18, 0]" :rotation="[0, Math.PI / 4, 0]">
      <TresOctahedronGeometry :args="[0.1]" />
      <TresMeshStandardMaterial color="#dbe8ff" :metalness="0.6" :roughness="0.2" />
    </TresMesh>
  </TresGroup>
</template>
