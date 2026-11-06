<script setup lang="ts">
import { computed, shallowRef, watch } from 'vue'
import { useLoop } from '@tresjs/core'
import { Vector3, type Group } from 'three'
import type { RotorPart } from './rotorParts'

const props = withDefaults(
  defineProps<{ spinKey: number; selfSpin?: boolean; accent?: string | null }>(),
  { selfSpin: true, accent: null },
)

const START_SPEED = 9
const FRICTION = 1.1

const rotor = shallowRef<Group>()
const wheel = shallowRef<Group>()
const track = shallowRef<Group>()
const tip = shallowRef<Group>()
let speed = props.selfSpin ? START_SPEED : 0
let time = 0

// Ruhepositionen der drei Teile; der Ursprung jeder Gruppe liegt in der Mitte des Teils.
const WHEEL_Y = 0.05
const TRACK_Y = -0.17
const TIP_Y = -0.42

const bladeColor = computed(() => props.accent ?? '#e9e6df')

watch(
  () => props.spinKey,
  () => {
    if (props.selfSpin) speed = START_SPEED
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

function parts(): RotorPart[] {
  const list: [Group | undefined, number][] = [
    [wheel.value, WHEEL_Y],
    [track.value, TRACK_Y],
    [tip.value, TIP_Y],
  ]
  return list.flatMap(([g, y]) =>
    g ? [{ group: g, rest: new Vector3(0, y, 0), pivot: new Vector3(0, y, 0) }] : [],
  )
}

defineExpose({ parts, scale: () => 1, root: () => rotor.value })
</script>

<template>
  <TresGroup ref="rotor">
    <TresGroup ref="tip" :position="[0, TIP_Y, 0]">
      <TresMesh :rotation="[Math.PI, 0, 0]">
        <TresConeGeometry :args="[0.22, 0.36, 32]" />
        <TresMeshStandardMaterial color="#20242c" :metalness="0.3" :roughness="0.5" />
      </TresMesh>
    </TresGroup>

    <TresGroup ref="track" :position="[0, TRACK_Y, 0]">
      <TresMesh :position="[0, -0.05, 0]">
        <TresCylinderGeometry :args="[0.3, 0.24, 0.1, 32]" />
        <TresMeshStandardMaterial color="#26262a" :metalness="0.4" :roughness="0.4" />
      </TresMesh>
      <TresMesh :position="[0, 0.05, 0]">
        <TresCylinderGeometry :args="[0.72, 0.68, 0.1, 64]" />
        <TresMeshStandardMaterial color="#c9d2e0" :metalness="1" :roughness="0.25" />
      </TresMesh>
    </TresGroup>

    <TresGroup ref="wheel" :position="[0, WHEEL_Y, 0]">
      <TresMesh :position="[0, -0.05, 0]">
        <TresCylinderGeometry :args="[0.58, 0.62, 0.14, 64]" />
        <TresMeshStandardMaterial color="#1b1b1e" :metalness="0.5" :roughness="0.35" />
      </TresMesh>
      <TresGroup v-for="a in blades" :key="a" :rotation="[0, a, 0]">
        <TresMesh :position="[0.72, -0.05, 0.08]" :rotation="[0, -0.5, 0]">
          <TresBoxGeometry :args="[0.42, 0.12, 0.16]" />
          <TresMeshStandardMaterial :color="bladeColor" :metalness="0.5" :roughness="0.3" />
        </TresMesh>
        <TresMesh :position="[0.5, 0.03, 0]">
          <TresBoxGeometry :args="[0.1, 0.04, 0.12]" />
          <TresMeshStandardMaterial color="#c8c8d0" :metalness="1" :roughness="0.3" />
        </TresMesh>
      </TresGroup>

      <TresMesh :position="[0, 0.05, 0]">
        <TresCylinderGeometry :args="[0.3, 0.34, 0.08, 48]" />
        <TresMeshStandardMaterial color="#d9e0ea" :metalness="1" :roughness="0.2" />
      </TresMesh>
      <TresMesh :position="[0, 0.1, 0]">
        <TresCylinderGeometry :args="[0.22, 0.24, 0.04, 48]" />
        <TresMeshStandardMaterial color="#c8c8d0" :metalness="0.3" :roughness="0.3" />
      </TresMesh>
      <TresMesh :position="[0, 0.12, 0]" :rotation="[Math.PI / 2, 0, 0]">
        <TresTorusGeometry :args="[0.23, 0.015, 12, 48]" />
        <TresMeshStandardMaterial color="#c8c8d0" :metalness="1" :roughness="0.25" />
      </TresMesh>
      <TresMesh :position="[0, 0.13, 0]" :rotation="[0, Math.PI / 4, 0]">
        <TresOctahedronGeometry :args="[0.1]" />
        <TresMeshStandardMaterial color="#1b1b1e" :metalness="0.6" :roughness="0.2" />
      </TresMesh>
    </TresGroup>
  </TresGroup>
</template>
