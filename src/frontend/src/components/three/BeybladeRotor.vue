<script setup lang="ts">
import { nextTick, ref, shallowRef, watch } from 'vue'
import { useLoop } from '@tresjs/core'
import { Box3, Vector3, type Group } from 'three'
import PartModel from './PartModel.vue'

const props = withDefaults(
  defineProps<{
    urls: string[]
    spinKey: number
    size?: number
    anchor?: 'center' | 'bottom'
    selfSpin?: boolean
  }>(),
  { size: 1.9, anchor: 'center', selfSpin: true },
)

const emit = defineEmits<{ ready: [] }>()

const START_SPEED = 9
const FRICTION = 1.1

const rotor = shallowRef<Group>()
const content = shallowRef<Group>()
const loaded = ref(0)
const fitted = ref(false)
let speed = props.selfSpin ? START_SPEED : 0

watch(loaded, async (count) => {
  if (count < props.urls.length || !content.value) return
  await nextTick()
  const group = content.value
  const box = new Box3().setFromObject(group)
  const size = box.getSize(new Vector3())
  const scale = props.size / Math.max(size.x, size.y, size.z, 1e-6)
  const center = box.getCenter(new Vector3())
  group.scale.setScalar(scale)
  group.position.set(
    -center.x * scale,
    props.anchor === 'bottom' ? -box.min.y * scale : -center.y * scale,
    -center.z * scale,
  )
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
</script>

<template>
  <TresGroup ref="rotor">
    <TresGroup ref="content" :visible="fitted">
      <PartModel v-for="url in urls" :key="url" :url="url" @loaded="loaded++" />
    </TresGroup>
  </TresGroup>
</template>
