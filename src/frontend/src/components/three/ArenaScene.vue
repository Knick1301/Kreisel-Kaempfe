<script setup lang="ts">
import { onMounted, onUnmounted, shallowRef, watch } from 'vue'
import { useLoop, useTres } from '@tresjs/core'
import {
  DoubleSide,
  Fog,
  Vector2,
  Vector3,
  MOUSE,
  TOUCH,
  type Group,
  type Mesh,
  type PerspectiveCamera,
  type WebGLRenderer,
} from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { baseBowlY, BEY_SIZE, STANDARD_ARENA } from '@/game/arenas'
import BeybladeRotor from './BeybladeRotor.vue'
import BowlDecor from './BowlDecor.vue'
import PlaceholderRotor from './PlaceholderRotor.vue'
import StudioEnvironment from './StudioEnvironment.vue'
import { makeMetallic } from './materials'
import { makeFloorTexture, makeShadowTexture } from './textures'

const props = defineProps<{ urls: string[] | null | undefined; spinKey: number }>()

const BG = '#0a0a0b'
const ACCENT = '#ededef'

// Showcase-Bowl: Standard-Stadion ohne Ridge und Taschen
const R = STANDARD_ARENA.radius
const bowlY = (r: number) => baseBowlY(STANDARD_ARENA, r)

const PLACEHOLDER_SCALE = BEY_SIZE / 1.9

const bowlProfile = [
  ...Array.from({ length: 33 }, (_, i) => {
    const r = (i / 32) * R
    return new Vector2(r, bowlY(r))
  }),
  new Vector2(R + 0.08, bowlY(R) + 0.1),
  new Vector2(R + 0.9, bowlY(R) + 0.1),
  new Vector2(R + 0.9, -1),
]
const shadowTexture = makeShadowTexture()
const floorTexture = makeFloorTexture()

const { scene, renderer } = useTres()
scene.value.fog = new Fog(BG, 9, 17)
scene.value.environmentIntensity = 0.6

const camera = shallowRef<PerspectiveCamera>()
const spinner = shallowRef<Group>()
const shadow = shallowRef<Mesh>()

const CAM_POS: [number, number, number] = [0, 3.85, 7.3]
const LOOK_AT = new Vector3(0, 1.05, 0)

const SPIN_DURATION = 2
const LOOP_R = 0.42
const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
let spinT = -1
let spinFrom = 0

function onModelReady() {
  makeMetallic(spinner.value)
  spinOnce()
}

function spinOnce() {
  if (reduceMotion || spinT >= 0 || !spinner.value) return
  spinFrom = spinner.value.rotation.y
  spinT = 0
}

const easeInOut = (t: number) => (t < 0.5 ? 4 * t * t * t : 1 - (-2 * t + 2) ** 3 / 2)

watch(() => props.spinKey, spinOnce)
watch(
  () => props.urls,
  (u) => {
    if (u === null) spinOnce()
  },
)

const canvas = () => (renderer as WebGLRenderer).domElement
let controls: OrbitControls | null = null

function setupControls(cam: PerspectiveCamera) {
  const c = new OrbitControls(cam, canvas())
  c.target.copy(LOOK_AT)
  c.mouseButtons = { LEFT: null, MIDDLE: MOUSE.DOLLY, RIGHT: MOUSE.ROTATE }
  c.touches = { ONE: null, TWO: TOUCH.DOLLY_ROTATE }
  c.enablePan = false
  c.enableDamping = true
  c.dampingFactor = 0.08
  c.rotateSpeed = 0.6
  c.zoomSpeed = 0.6
  c.minDistance = 4.5
  c.maxDistance = 11
  c.minPolarAngle = 0.15
  c.maxPolarAngle = 1.38
  c.update()
  c.saveState()
  canvas().style.touchAction = 'pan-y'
  return c
}

function resetView() {
  controls?.reset()
}

onMounted(() => canvas().addEventListener('dblclick', resetView))
onUnmounted(() => {
  canvas().removeEventListener('dblclick', resetView)
  controls?.dispose()
  scene.value.fog = null
  shadowTexture.dispose()
  floorTexture.dispose()
})

let placeholderSpun = false

const { onBeforeRender } = useLoop()

onBeforeRender(({ delta }) => {
  if (!camera.value || !spinner.value) return
  if (!controls) controls = setupControls(camera.value)
  controls.update()

  if (props.urls === null && !placeholderSpun) {
    placeholderSpun = true
    spinOnce()
  }

  if (spinT >= 0) {
    const s = spinner.value
    spinT = Math.min(1, spinT + delta / SPIN_DURATION)
    const e = easeInOut(spinT)
    s.rotation.y = spinFrom + e * Math.PI * 2

    const theta = e * Math.PI * 2
    const x = LOOP_R * Math.sin(theta)
    const z = -LOOP_R * (1 - Math.cos(theta))
    const y = bowlY(Math.hypot(x, z))
    s.position.set(x, y, z)
    shadow.value?.position.set(x, y + 0.006, z)

    const wobble = Math.sin(spinT * Math.PI) * 0.035
    s.rotation.x = Math.sin(spinT * Math.PI * 6) * wobble
    s.rotation.z = Math.cos(spinT * Math.PI * 6) * wobble
    if (spinT >= 1) {
      spinT = -1
      s.rotation.x = 0
      s.rotation.z = 0
      s.position.set(0, 0, 0)
      shadow.value?.position.set(0, 0.006, 0)
    }
  }
})
</script>

<template>
  <TresPerspectiveCamera ref="camera" :position="CAM_POS" :fov="34" />
  <StudioEnvironment />

  <TresMesh>
    <TresLatheGeometry :args="[bowlProfile, 128]" />
    <TresMeshBasicMaterial :map="floorTexture" :side="DoubleSide" />
  </TresMesh>

  <BowlDecor :height-at="bowlY" />

  <TresMesh :position="[0, bowlY(R) + 0.1, 0]" :rotation="[-Math.PI / 2, 0, 0]">
    <TresTorusGeometry :args="[R + 0.08, 0.014, 8, 240]" />
    <TresMeshBasicMaterial :color="ACCENT" />
  </TresMesh>

  <TresMesh ref="shadow" :position="[0, 0.006, 0]" :rotation="[-Math.PI / 2, 0, 0]">
    <TresPlaneGeometry :args="[BEY_SIZE * 1.4, BEY_SIZE * 1.4]" />
    <TresMeshBasicMaterial :map="shadowTexture" transparent :depth-write="false" />
  </TresMesh>

  <TresGroup ref="spinner">
    <BeybladeRotor
      v-if="props.urls"
      :key="props.urls.join('|')"
      :urls="props.urls"
      :spin-key="spinKey"
      :size="BEY_SIZE"
      anchor="bottom"
      :self-spin="false"
      @ready="onModelReady"
    />
    <TresGroup
      v-else-if="props.urls === null"
      :scale="PLACEHOLDER_SCALE"
      :position="[0, 0.6 * PLACEHOLDER_SCALE, 0]"
    >
      <PlaceholderRotor :spin-key="spinKey" :self-spin="false" />
    </TresGroup>
  </TresGroup>
</template>
