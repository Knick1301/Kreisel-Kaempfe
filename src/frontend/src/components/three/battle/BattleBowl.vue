<script setup lang="ts">
import { onUnmounted } from 'vue'
import { DoubleSide } from 'three'
import { bowlY, type ArenaDefinition } from '@/game/arenas'
import BowlDecor from '../BowlDecor.vue'
import { makeFloorTexture } from '../textures'
import { buildArenaGeometries } from './arenaGeometry'

/** Kampf-Stadion: Bowl mit Tornado-Ridge, Rand mit Ring-Out-Taschen, alles aus der Arena-Definition. */
const props = defineProps<{ arena: ArenaDefinition }>()

const ACCENT = '#ededef'
const geo = buildArenaGeometries(props.arena)
const floorTexture = makeFloorTexture()
const heightAt = (r: number) => bowlY(props.arena, r)

onUnmounted(() => {
  geo.dispose()
  floorTexture.dispose()
})
</script>

<template>
  <TresMesh :geometry="geo.bowl">
    <TresMeshStandardMaterial
      :map="floorTexture"
      :metalness="0.6"
      :roughness="0.38"
      :env-map-intensity="0.8"
      :side="DoubleSide"
    />
  </TresMesh>

  <BowlDecor :height-at="heightAt" />

  <TresMesh :geometry="geo.ridgeLine">
    <TresMeshBasicMaterial color="#34343c" :side="DoubleSide" />
  </TresMesh>

  <TresMesh :geometry="geo.metal">
    <TresMeshStandardMaterial
      color="#141418"
      :metalness="0.9"
      :roughness="0.28"
      :side="DoubleSide"
    />
  </TresMesh>

  <TresMesh :geometry="geo.pocketFloors">
    <TresMeshStandardMaterial
      color="#0a0a0c"
      :metalness="0.5"
      :roughness="0.55"
      :side="DoubleSide"
    />
  </TresMesh>

  <TresMesh :geometry="geo.accents">
    <TresMeshBasicMaterial :color="ACCENT" :side="DoubleSide" />
  </TresMesh>
</template>
