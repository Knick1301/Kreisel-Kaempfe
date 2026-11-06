<script setup lang="ts">
import { computed, watch } from 'vue'
import { useGLTF } from '@tresjs/cientos'
import { clone as cloneSkinned } from 'three/examples/jsm/utils/SkeletonUtils.js'

const props = withDefaults(defineProps<{ url: string; clone?: boolean }>(), { clone: false })
const emit = defineEmits<{ loaded: [] }>()

const { state } = useGLTF(props.url)

// useGLTF cached die Szene pro URL. Ein three-Objekt kann nur einen Parent haben, deshalb braucht jede
// weitere Instanz derselben URL (zwei gleiche Kreisel in einer Arena) einen Klon. Die Modelle enthalten
// SkinnedMeshes: Object3D.clone() würde sie ans Skelett des Originals binden, SkeletonUtils bindet neu.
const object = computed(() =>
  props.clone && state.value ? cloneSkinned(state.value.scene) : state.value?.scene,
)

watch(state, (gltf) => {
  if (gltf) emit('loaded')
})
</script>

<template>
  <primitive v-if="object" :object="object" />
</template>
