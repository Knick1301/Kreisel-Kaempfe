<script setup lang="ts">
import { onUnmounted } from 'vue'
import { useTres } from '@tresjs/core'
import { PMREMGenerator, type WebGLRenderer } from 'three'
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js'


const { scene, renderer } = useTres()

const pmrem = new PMREMGenerator(renderer as WebGLRenderer)
const room = new RoomEnvironment()
const envMap = pmrem.fromScene(room, 0.04).texture
scene.value.environment = envMap

onUnmounted(() => {
  scene.value.environment = null
  envMap.dispose()
  room.dispose()
  pmrem.dispose()
})
</script>

<template>
  <slot />
</template>
