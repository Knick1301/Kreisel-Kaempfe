<script setup lang="ts">
import { onUnmounted, shallowRef, watch } from 'vue'
import { useLoop } from '@tresjs/core'
import { Group, Vector3 } from 'three'
import { BEY_SIZE } from '@/game/arenas'
import { designKey, type PartDesigns } from '@/game/partDesigns'
import {
  buildTip,
  buildTrack,
  buildWheel,
  MaterialCache,
  stackHeights,
  type BuiltPart,
} from './parts/partBuilders'
import type { RotorPart } from './rotorParts'

/**
 * Kreisel aus prozeduralen Teilen (Fusion Wheel, Spin Track, Tip). Spitze unten bei y = 0.
 * Wird ein Teil getauscht, baut nur dieses neu und rastet mit einer kurzen Fall-Animation ein.
 */
const props = withDefaults(
  defineProps<{
    designs: PartDesigns
    /** Einrast-Animation und Explosionsansicht (Baukasten); im Kampf aus */
    animated?: boolean
    exploded?: boolean
  }>(),
  { animated: false, exploded: false },
)

const SCALE = BEY_SIZE / 1.9
const EXPLODE = [0.55, 0.26, 0] as const
const reduceMotion = globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false

const root = shallowRef<Group>()
const mats = new MaterialCache()
const holders = [new Group(), new Group(), new Group()] as const
const built: (BuiltPart | null)[] = [null, null, null]
const keys = ['', '', '']
const rest = [new Vector3(), new Vector3(), new Vector3()] as const
const offset = [0, 0, 0]
const drop = [0, 0, 0]

function rebuild(index: 0 | 1 | 2, initial: boolean) {
  const d = props.designs
  const design = index === 0 ? d.wheel : index === 1 ? d.track : d.tip
  const key = designKey(design)
  if (keys[index] === key) return false
  keys[index] = key
  built[index]?.dispose()
  holders[index].clear()
  const part =
    index === 0
      ? buildWheel(d.wheel, mats)
      : index === 1
        ? buildTrack(d.track, mats)
        : buildTip(d.tip, mats)
  holders[index].add(part.group)
  built[index] = part
  if (!initial && props.animated && !reduceMotion) drop[index] = 1
  return true
}

function layout() {
  const h = stackHeights(props.designs.track)
  rest[0].set(0, h.wheelY, 0)
  rest[1].set(0, h.trackY, 0)
  rest[2].set(0, h.tipY, 0)
  holders.forEach((g, i) => g.position.copy(rest[i]!))
}

;([0, 1, 2] as const).forEach((i) => rebuild(i, true))
layout()

watch(
  () => props.designs,
  () => {
    ;([0, 1, 2] as const).forEach((i) => rebuild(i, false))
    layout()
  },
  { deep: true },
)

const { onBeforeRender } = useLoop()
onBeforeRender(({ delta }) => {
  if (!props.animated) return
  const follow = 1 - Math.exp(-delta * 9)
  for (let i = 0; i < 3; i++) {
    const g = holders[i]!
    offset[i] = offset[i]! + ((props.exploded ? EXPLODE[i]! : 0) - offset[i]!) * follow
    let fall = 0
    if (drop[i]! > 0) {
      drop[i] = Math.max(0, drop[i]! - delta / 0.6)
      const u = 1 - drop[i]!
      // fällt von oben ein, federt kurz nach
      fall = 1.1 * (1 - u) ** 3 - 0.04 * Math.sin(u * Math.PI) * (1 - u)
      g.rotation.y += delta * 14 * drop[i]!
    }
    g.position.y = rest[i]!.y + offset[i]! + fall
  }
})

onUnmounted(() => {
  built.forEach((p) => p?.dispose())
  mats.dispose()
})

defineExpose({
  /** Wheel, Track, Tip mit Ruheposition und Drehpunkt (Mitte des Teils) */
  parts: (): RotorPart[] =>
    holders.map((group, i) => ({ group, rest: rest[i]!.clone(), pivot: rest[i]!.clone() })),
  scale: () => SCALE,
  root: () => root.value,
})
</script>

<template>
  <TresGroup ref="root" :scale="SCALE">
    <primitive :object="holders[0]" />
    <primitive :object="holders[1]" />
    <primitive :object="holders[2]" />
  </TresGroup>
</template>
