<script setup lang="ts">
import { computed, watch } from 'vue'
import { useGLTF } from '@tresjs/cientos'
import { clone as cloneSkinned } from 'three/examples/jsm/utils/SkeletonUtils.js'
import { mergeByMaterial } from './mergeMeshes'

const props = withDefaults(defineProps<{ url: string; clone?: boolean }>(), { clone: false })
const emit = defineEmits<{ loaded: [] }>()

const { state } = useGLTF(props.url)

// useGLTF cached die Szene pro URL. Ein three-Objekt kann nur einen Parent haben, deshalb braucht jede
// weitere Instanz derselben URL (zwei gleiche Kreisel in einer Arena) einen Klon. Die Modelle enthalten
// SkinnedMeshes: Object3D.clone() würde sie ans Skelett des Originals binden, SkeletonUtils bindet neu.
const object = computed(() => {
  const scene = state.value?.scene
  if (!scene) return undefined
  // Einzelteile mit gleichem Material zusammenfassen: deutlich weniger Draw Calls
  mergeByMaterial(scene)
  return props.clone ? cloneSkinned(scene) : scene
})

watch(state, (gltf) => {
  if (gltf) emit('loaded')
})
</script>

<template>
  <primitive v-if="object" :object="object" />
</template>
