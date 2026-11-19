<script setup lang="ts">
import { ref, shallowRef, watch } from 'vue'
import { useLoop } from '@tresjs/core'
import { Group, Vector3 } from 'three'
import { BEY_SIZE } from '@/game/arenas'
import PartModel from './PartModel.vue'
import { makeMetallic } from './materials'
import type { RotorPart } from './rotorParts'

/**
 * Kreisel aus den drei GLB-Teilen für den Baukasten. Die Teile liegen in ihren Dateien schon richtig
 * gestapelt (Blender-Konvention aus tools/blender/build_parts.py). Wird ein Teil getauscht, fällt nur
 * dieses neu ein und rastet ein; die Explosionsansicht zieht die Teile auseinander.
 */
const props = withDefaults(
  defineProps<{
    /** Fusion Wheel, Spin Track, Tip */
    urls: string[]
    exploded?: boolean
  }>(),
  { exploded: false },
)
const emit = defineEmits<{ ready: [] }>()

/** Größter Durchmesser der Teile in Modell-Einheiten (Wheel ~0,48 inkl. Spitzen) */
const MODEL_SIZE = 0.5
const SCALE = BEY_SIZE / MODEL_SIZE
/** Spitze knapp unter 0 (Kontaktflächen ragen etwas heraus) */
const LIFT = 0.012
/** Abstand in der Explosionsansicht, in Modell-Einheiten */
const EXPLODE = [0.16, 0.075, 0] as const
/** Drehpunkte für den Burst: Mitte des jeweiligen Höhenbands */
const PIVOT_Y = [0.29, 0.19, 0.08] as const
const reduceMotion = globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false

const root = shallowRef<Group>()
const holders = [shallowRef<Group>(), shallowRef<Group>(), shallowRef<Group>()] as const
const loaded = ref(0)
const offset = [0, 0, 0]
const drop = [0, 0, 0]
let previous = [...props.urls]

watch(
  () => [...props.urls],
  (next) => {
    next.forEach((url, i) => {
      if (url !== previous[i] && !reduceMotion) drop[i] = 1
    })
    previous = next
  },
)

function onLoaded(i: number) {
  loaded.value++
  // Kunststoff des Hells-Scythe-Sets metallisch machen, Blender-Teile bleiben unverändert
  makeMetallic(holders[i]!.value)
  if (loaded.value === 3) emit('ready')
}

const { onBeforeRender } = useLoop()
onBeforeRender(({ delta }) => {
  const follow = 1 - Math.exp(-delta * 9)
  for (let i = 0; i < 3; i++) {
    const g = holders[i]!.value
    if (!g) continue
    offset[i] = offset[i]! + ((props.exploded ? EXPLODE[i]! : 0) - offset[i]!) * follow
    let fall = 0
    if (drop[i]! > 0) {
      drop[i] = Math.max(0, drop[i]! - delta / 0.6)
      const u = 1 - drop[i]!
      // fällt von oben ein, dreht dabei und federt kurz nach
      fall = 0.35 * (1 - u) ** 3 - 0.012 * Math.sin(u * Math.PI) * (1 - u)
      g.rotation.y += delta * 14 * drop[i]!
    }
    g.position.y = offset[i]! + fall
  }
})

defineExpose({
  /** Wheel, Track, Tip mit Ruheposition und Drehpunkt */
  parts: (): RotorPart[] =>
    holders.flatMap((h, i) =>
      h.value
        ? [{ group: h.value, rest: new Vector3(), pivot: new Vector3(0, PIVOT_Y[i]!, 0) }]
        : [],
    ),
  scale: () => SCALE,
  root: () => root.value,
})
</script>

<template>
  <TresGroup ref="root" :scale="SCALE" :position="[0, LIFT * SCALE, 0]">
    <TresGroup
      v-for="(url, i) in urls"
      :key="i"
      :ref="(el: unknown) => (holders[i]!.value = el as Group)"
    >
      <PartModel :key="url" :url="url" clone @loaded="onLoaded(i)" />
    </TresGroup>
  </TresGroup>
</template>
