<script setup lang="ts">
import { onMounted, onUnmounted, shallowRef, watch } from 'vue'
import { useLoop, useTres } from '@tresjs/core'
import {
  AdditiveBlending,
  CanvasTexture,
  TextureLoader,
  type Texture,
  DoubleSide,
  Fog,
  SRGBColorSpace,
  Vector2,
  Vector3,
  MeshStandardMaterial,
  MOUSE,
  PlaneGeometry,
  TOUCH,
  type Group,
  type Mesh,
  type PerspectiveCamera,
  type WebGLRenderer,
} from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import BeybladeRotor from './BeybladeRotor.vue'
import PlaceholderRotor from './PlaceholderRotor.vue'
import StudioEnvironment from './StudioEnvironment.vue'

const props = defineProps<{ urls: string[] | null | undefined; spinKey: number }>()

const BG = '#0a0a0b'
const ACCENT = '#ededef'

const R = 4
const K = 0.05
const bowlY = (r: number) => K * r * r

const BEY_SIZE = 1.45
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

function makeShadowTexture() {
  const c = document.createElement('canvas')
  c.width = c.height = 128
  const g = c.getContext('2d')!
  const grad = g.createRadialGradient(64, 64, 0, 64, 64, 64)
  grad.addColorStop(0, 'rgba(0,0,0,0.65)')
  grad.addColorStop(1, 'rgba(0,0,0,0)')
  g.fillStyle = grad
  g.fillRect(0, 0, 128, 128)
  return new CanvasTexture(c)
}
const shadowTexture = makeShadowTexture()

function makeFloorTexture() {
  const c = document.createElement('canvas')
  c.width = 4
  c.height = 256
  const g = c.getContext('2d')!
  const grad = g.createLinearGradient(0, 0, 0, 256)
  grad.addColorStop(0, '#2a2a2f')
  grad.addColorStop(0.18, '#1c1c20')
  grad.addColorStop(0.45, '#111114')
  grad.addColorStop(0.88, '#0b0b0d')
  grad.addColorStop(1, '#060607')
  g.fillStyle = grad
  g.fillRect(0, 0, 4, 256)
  const tex = new CanvasTexture(c)
  tex.colorSpace = SRGBColorSpace
  tex.flipY = false
  return tex
}
const floorTexture = makeFloorTexture()

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

function makeMetallic() {
  spinner.value?.traverse((obj) => {
    const mesh = obj as Mesh
    if (!mesh.isMesh) return
    const mats = Array.isArray(mesh.material) ? mesh.material : [mesh.material]
    const next = mats.map((m) => {
      if (!(m instanceof MeshStandardMaterial) || m.transparent || m.opacity < 1) return m
      const c = m.clone()
      c.metalness = Math.max(c.metalness, 0.85)
      c.roughness = Math.min(Math.max(c.roughness, 0.22), 0.38)
      return c
    })
    mesh.material = Array.isArray(mesh.material) ? next : next[0]!
  })
}

function onModelReady() {
  makeMetallic()
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
  logoTexture.value?.dispose()
  logoGeometry.dispose()
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
