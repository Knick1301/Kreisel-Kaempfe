<script setup lang="ts">
import { nextTick, ref, shallowRef, watch } from 'vue'
import { useLoop } from '@tresjs/core'
import { Box3, Matrix4, Vector3, type Group } from 'three'
import PartModel from './PartModel.vue'
import type { RotorPart } from './rotorParts'

const props = withDefaults(
  defineProps<{
    urls: string[]
    spinKey: number
    size?: number
    anchor?: 'center' | 'bottom'
    selfSpin?: boolean
    /** GLB-Szenen klonen statt die gecachte Instanz zu verwenden */
    clone?: boolean
  }>(),
  { size: 1.9, anchor: 'center', selfSpin: true, clone: false },
)

const emit = defineEmits<{ ready: [] }>()

const START_SPEED = 9
const FRICTION = 1.1

const rotor = shallowRef<Group>()
const content = shallowRef<Group>()
const partGroups: (Group | undefined)[] = []
const loaded = ref(0)
const fitted = ref(false)
let speed = props.selfSpin ? START_SPEED : 0
let parts: RotorPart[] = []

function setPart(el: unknown, i: number) {
  partGroups[i] = (el as Group | null) ?? undefined
}

watch(loaded, async (count) => {
  if (count < props.urls.length || !content.value) return
  await nextTick()
  const group = content.value
  // Box im eigenen Koordinatensystem messen, nicht in Weltkoordinaten: In der Arena steht und dreht der
  // Rotor irgendwo, die Zentrierung würde das Modell sonst in den Ursprung ziehen. Dafür kurz abkoppeln.
  const parent = group.parent
  parent?.remove(group)
  group.updateMatrixWorld(true)
  const box = new Box3().setFromObject(group)
  parent?.add(group)
  const size = box.getSize(new Vector3())
  const scale = props.size / Math.max(size.x, size.y, size.z, 1e-6)
  const center = box.getCenter(new Vector3())
  group.scale.setScalar(scale)
  group.position.set(
    -center.x * scale,
    props.anchor === 'bottom' ? -box.min.y * scale : -center.y * scale,
    -center.z * scale,
  )
  // Drehpunkte der Teile (für den Burst) im Koordinatensystem von `content`
  group.updateWorldMatrix(true, true)
  const toContent = new Matrix4().copy(group.matrixWorld).invert()
  parts = partGroups.flatMap((g) => {
    if (!g) return []
    const b = new Box3().setFromObject(g).applyMatrix4(toContent)
    return [{ group: g, rest: new Vector3(), pivot: b.getCenter(new Vector3()) }]
  })
  fitted.value = true
  emit('ready')
  if (props.selfSpin) speed = START_SPEED
})

watch(
  () => props.spinKey,
  () => {
    if (props.selfSpin) speed = START_SPEED
  },
)

const { onBeforeRender } = useLoop()

onBeforeRender(({ delta }) => {
  if (!rotor.value || speed < 0.01) return
  rotor.value.rotation.y += speed * delta
  speed *= Math.exp(-FRICTION * delta)
})

defineExpose({
  /** Wheel, Track, Tip mit Ruheposition und Drehpunkt, erst nach `ready` gefüllt */
  parts: () => parts,
  /** Skalierung von Modell- auf Welt-Einheiten */
  scale: () => content.value?.scale.x ?? 1,
  /** Gruppe, in der die Teile liegen */
  root: () => content.value,
})
</script>

<template>
  <TresGroup ref="rotor">
    <TresGroup ref="content" :visible="fitted">
      <TresGroup v-for="(url, i) in urls" :key="url" :ref="(el: unknown) => setPart(el, i)">
        <PartModel :url="url" :clone="clone" @loaded="loaded++" />
      </TresGroup>
    </TresGroup>
  </TresGroup>
</template>
