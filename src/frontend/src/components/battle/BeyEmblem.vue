<script setup lang="ts">
import { computed } from 'vue'
import type { FighterDto } from '@/api'
import { designsFor, wheelByStats, type WheelDesign, type WheelShape } from '@/game/partDesigns'

/**
 * Emblem eines Kreisels: Draufsicht auf sein Fusion Wheel (Farben, Klingenform und -zahl, Stern auf der Nabe).
 * Als SVG-Gruppe um den Ursprung, Radius 40, zum Einbetten in ein eigenes SVG.
 */
const props = defineProps<{ fighter: FighterDto | undefined }>()

const wheel = computed<WheelDesign>(() => {
  const f = props.fighter
  if (f?.parts?.length === 3) return designsFor(f.parts).wheel
  return wheelByStats(f?.stats.attack ?? 3, f?.stats.defense ?? 3)
})

/** Klinge als Pfad, nach oben zeigend, Nabe im Ursprung. Die Form folgt grob der Wheel-Form. */
const BLADES: Record<WheelShape, string> = {
  scythe: 'M -5 -10 C -14 -24 -6 -38 10 -40 C 2 -32 2 -22 6 -12 Z',
  fortress: 'M -9 -10 L -11 -34 L 11 -34 L 9 -10 Z',
  vortex: 'M -6 -10 C -18 -22 -14 -36 2 -40 C -2 -30 4 -20 8 -11 Z',
  fang: 'M -7 -10 L 0 -42 L 7 -10 Z',
  moon: 'M -10 -12 C -16 -30 0 -40 14 -36 C 2 -32 -2 -22 4 -12 Z',
  phantom: 'M -4 -10 C -16 -18 -16 -34 -4 -40 C -6 -30 4 -22 7 -11 Z',
  feather: 'M -6 -10 C -12 -22 -10 -34 0 -41 C 6 -34 8 -22 6 -10 Z',
}

const blades = computed(() => {
  const n = Math.max(2, Math.min(8, wheel.value.blades))
  return Array.from({ length: n }, (_, i) => (360 / n) * i)
})

const star = computed(() => {
  const points = wheel.value.blades >= 5 ? 5 : wheel.value.blades + 2
  const pts: string[] = []
  for (let i = 0; i < points * 2; i++) {
    const a = (i / (points * 2)) * Math.PI * 2 - Math.PI / 2
    const r = i % 2 === 0 ? 9 : 3.6
    pts.push(`${(Math.cos(a) * r).toFixed(2)},${(Math.sin(a) * r).toFixed(2)}`)
  }
  return pts.join(' ')
})
</script>

<template>
  <g>
    <circle r="40" :fill="wheel.layer" opacity="0.9" />
    <circle r="40" fill="none" :stroke="wheel.metal" stroke-width="3" opacity="0.8" />
    <path
      v-for="deg in blades"
      :key="deg"
      :d="BLADES[wheel.shape]"
      :transform="`rotate(${deg})`"
      :fill="wheel.metal"
      :stroke="wheel.accent"
      stroke-width="1.2"
    />
    <circle r="12" :fill="wheel.metal" :stroke="wheel.accent" stroke-width="1.5" />
    <polygon :points="star" :fill="wheel.accent" />
  </g>
</template>
