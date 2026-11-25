<script setup lang="ts">
import { onUnmounted, shallowRef } from 'vue'
import { AdditiveBlending, PlaneGeometry, SRGBColorSpace, TextureLoader, type Texture } from 'three'

/** Boden-Markierungen der Bowl (Ringe, Skala, Akzentbögen, Logo), auf eine beliebige Höhenfunktion gelegt. */
const props = defineProps<{ heightAt: (r: number) => number }>()

const ACCENT = '#ededef'
const bowlY = props.heightAt

const rings = [1.1, 2.2, 3.4].map((r) => ({ r, y: bowlY(r) + 0.004 }))

const TICK_R = 2.7
const ticks = Array.from({ length: 120 }, (_, i) => {
  const major = i % 10 === 0
  const len = major ? 0.22 : 0.08
  return {
    angle: (i / 120) * Math.PI * 2,
    len,
    color: major ? '#6a6a74' : '#2c2c33',
    x: TICK_R + len / 2,
    y: bowlY(TICK_R + len / 2) + 0.015,
  }
})

const arcs = [0.35, Math.PI + 0.35].map((start) => ({ start, length: 0.7 }))

const LOGO_SIZE = 2.5
const logoTexture = shallowRef<Texture | null>(null)
new TextureLoader().load('/logokreisel.png', (tex) => {
  tex.colorSpace = SRGBColorSpace
  logoTexture.value = tex
})

function makeLogoGeometry() {
  const g = new PlaneGeometry(LOGO_SIZE, LOGO_SIZE, 48, 48)
  g.rotateX(-Math.PI / 2)
  const pos = g.attributes.position!
  for (let i = 0; i < pos.count; i++) {
    pos.setY(i, bowlY(Math.hypot(pos.getX(i), pos.getZ(i))) + 0.003)
  }
  pos.needsUpdate = true
  g.computeBoundingSphere()
  return g
}
const logoGeometry = makeLogoGeometry()

onUnmounted(() => {
  logoTexture.value?.dispose()
  logoGeometry.dispose()
})
</script>

<template>
  <TresMesh
    v-for="ring in rings"
    :key="ring.r"
    :position="[0, ring.y, 0]"
    :rotation="[-Math.PI / 2, 0, 0]"
  >
    <TresRingGeometry :args="[ring.r - 0.006, ring.r + 0.006, 160]" />
    <TresMeshBasicMaterial color="#1d1d22" />
  </TresMesh>

  <TresGroup v-for="(t, i) in ticks" :key="`t${i}`" :rotation="[0, -t.angle, 0]">
    <TresMesh :position="[t.x, t.y, 0]" :rotation="[-Math.PI / 2, 0, 0]">
      <TresPlaneGeometry :args="[t.len, 0.012]" />
      <TresMeshBasicMaterial :color="t.color" />
    </TresMesh>
  </TresGroup>

  <TresMesh
    v-for="arc in arcs"
    :key="arc.start"
    :position="[0, bowlY(1.1) + 0.006, 0]"
    :rotation="[-Math.PI / 2, 0, 0]"
  >
    <TresRingGeometry :args="[1.08, 1.12, 48, 1, arc.start, arc.length]" />
    <TresMeshBasicMaterial :color="ACCENT" />
  </TresMesh>

  <TresMesh v-if="logoTexture" :geometry="logoGeometry" :render-order="1">
    <TresMeshBasicMaterial
      :map="logoTexture"
      :blending="AdditiveBlending"
      :opacity="0.5"
      transparent
      :depth-write="false"
    />
  </TresMesh>
</template>
