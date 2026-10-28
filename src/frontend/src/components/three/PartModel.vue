<script setup lang="ts">
import { watch } from 'vue'
import { useGLTF } from '@tresjs/cientos'

const props = defineProps<{ url: string }>()
const emit = defineEmits<{ loaded: [] }>()

const { state } = useGLTF(props.url)

watch(state, (gltf) => {
  if (gltf) emit('loaded')
})
</script>

<template>
  <primitive v-if="state" :object="state.scene" />
</template>
