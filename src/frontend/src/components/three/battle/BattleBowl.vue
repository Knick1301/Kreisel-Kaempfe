<script setup lang="ts">
import { onUnmounted } from 'vue'
import { DoubleSide } from 'three'
import { bowlY, type ArenaDefinition } from '@/game/arenas'
import BowlDecor from '../BowlDecor.vue'
import { makeFloorTexture } from '../textures'
import { buildArenaGeometries } from './arenaGeometry'

/** Kampf-Stadion: Bowl mit Tornado-Ridge, Rand mit Ring-Out-Taschen, alles aus der Arena-Definition. */
const props = defineProps<{ arena: ArenaDefinition }>()

const theme = props.arena.theme
const geo = buildArenaGeometries(props.arena)
const floorTexture = makeFloorTexture(theme.floor)
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
    <TresMeshBasicMaterial :color="theme.ridgeLine" :side="DoubleSide" />
  </TresMesh>

  <TresMesh :geometry="geo.metal">
    <TresMeshStandardMaterial
      :color="theme.metal"
      :metalness="0.9"
      :roughness="0.28"
      :side="DoubleSide"
    />
  </TresMesh>

  <TresMesh :geometry="geo.pocketFloors">
    <TresMeshStandardMaterial
      :color="theme.pocketFloor"
      :emissive="theme.pocketGlow ? theme.pocketFloor : '#000000'"
      :emissive-intensity="theme.pocketGlow ? 1.4 : 0"
      :metalness="0.5"
      :roughness="0.55"
      :side="DoubleSide"
    />
  </TresMesh>

  <TresMesh :geometry="geo.accents">
    <TresMeshBasicMaterial :color="theme.accent" :side="DoubleSide" />
  </TresMesh>

  <!-- Glühen aus den Lava-Taschen -->
  <template v-if="theme.pocketGlow">
    <TresPointLight
      v-for="(p, i) in arena.pockets"
      :key="i"
      :position="[Math.cos(p) * (arena.radius + 0.45), 0.6, Math.sin(p) * (arena.radius + 0.45)]"
      :color="theme.pocketFloor"
      :intensity="6"
      :distance="4"
      :decay="2"
    />
  </template>
</template>
