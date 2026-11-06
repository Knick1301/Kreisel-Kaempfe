<script setup lang="ts">
import { onUnmounted } from 'vue'
import { DoubleSide } from 'three'
import { arenaVariant, bowlY, type ArenaDefinition } from '@/game/arenas'
import BowlDecor from '../BowlDecor.vue'
import { makeFloorTexture } from '../textures'
import { buildCycloneArena } from '../arenas/cycloneArena'
import { buildGlacierArena } from '../arenas/glacierArena'
import { buildOctagonArena } from '../arenas/octagonArena'
import type { BuiltArena } from '../arenas/kit'
import { buildArenaGeometries } from './arenaGeometry'

/**
 * Kampf-Stadion aus Arena-Definition und optischer Variante. Das Standard-Stadion ist eine Bowl mit Rand und
 * Taschen, die übrigen Bauweisen kommen aus `components/three/arenas`.
 */
const props = defineProps<{ arena: ArenaDefinition; variant?: string | null }>()

const variant = arenaVariant(props.arena, props.variant)
const palette = variant.palette
const classic = props.arena.style === 'classic'

const builders: Record<string, (a: ArenaDefinition, v: typeof variant) => BuiltArena> = {
  octagon: buildOctagonArena,
  cyclone: buildCycloneArena,
  glacier: buildGlacierArena,
}
const built = classic ? null : builders[props.arena.style]!(props.arena, variant)

const geo = classic ? buildArenaGeometries(props.arena) : null
const floorTexture = classic ? makeFloorTexture(palette.floor) : null
const heightAt = (r: number) => bowlY(props.arena, r)

onUnmounted(() => {
  geo?.dispose()
  floorTexture?.dispose()
  built?.dispose()
})
</script>

<template>
  <primitive v-if="built" :object="built.group" />

  <template v-else-if="geo">
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
      <TresMeshBasicMaterial :color="palette.line" :side="DoubleSide" />
    </TresMesh>

    <TresMesh :geometry="geo.metal">
      <TresMeshStandardMaterial
        :color="palette.metal"
        :metalness="0.9"
        :roughness="0.28"
        :side="DoubleSide"
      />
    </TresMesh>

    <TresMesh :geometry="geo.pocketFloors">
      <TresMeshStandardMaterial
        :color="palette.pocketFloor"
        :emissive="palette.pocketGlow ? palette.pocketFloor : '#000000'"
        :emissive-intensity="palette.pocketGlow ? 1.4 : 0"
        :metalness="0.5"
        :roughness="0.55"
        :side="DoubleSide"
      />
    </TresMesh>

    <TresMesh :geometry="geo.accents">
      <TresMeshBasicMaterial :color="palette.accent" :side="DoubleSide" />
    </TresMesh>

    <!-- Glühen aus leuchtenden Taschen -->
    <template v-if="palette.pocketGlow">
      <TresPointLight
        v-for="(p, i) in arena.pockets"
        :key="i"
        :position="[Math.cos(p) * (arena.radius + 0.45), 0.6, Math.sin(p) * (arena.radius + 0.45)]"
        :color="palette.pocketFloor"
        :intensity="6"
        :distance="4"
        :decay="2"
      />
    </template>
  </template>
</template>
