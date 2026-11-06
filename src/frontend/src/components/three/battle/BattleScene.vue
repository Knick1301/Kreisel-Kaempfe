<script setup lang="ts">
import { onMounted, onUnmounted, shallowRef, watch } from 'vue'
import { useLoop, useTres } from '@tresjs/core'
import {
  Fog,
  Group,
  Mesh as ThreeMesh,
  MeshBasicMaterial as ThreeBasicMaterial,
  MOUSE,
  PlaneGeometry,
  RingGeometry,
  TOUCH,
  Vector3,
  type BufferAttribute,
  type Mesh,
  type MeshBasicMaterial,
  type PerspectiveCamera,
  type PointLight,
  type WebGLRenderer,
} from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import type { BattleReport, FighterDto } from '@/api'
import { BEY_SIZE, bowlY, pocketFloorY } from '@/game/arenas'
import { CameraDirector } from '@/game/cameraDirector'
import {
  createPartPoses,
  createPose,
  type Choreography,
  type FighterPose,
} from '@/game/choreographer'
import type { BattleClock } from '@/game/clock'
import { modelKey } from '@/game/partDesigns'
import { hashString, seededStream } from '@/game/prng'
import StudioEnvironment from '../StudioEnvironment.vue'
import { makeShadowTexture } from '../textures'
import BattleBowl from './BattleBowl.vue'
import BattleFighter from './BattleFighter.vue'
import { SparkPool } from './sparks'

const props = defineProps<{
  report: BattleReport
  choreo: Choreography
  clock: BattleClock
  /** ruhige Version: kein Shake, kein Blitz */
  calm: boolean
  /** nur Seite A zeigen (Vorschau vor dem Kampf) */
  solo?: boolean
}>()

const BG = '#0a0a0b'
const arena = props.choreo.arena

const { scene, renderer } = useTres()
scene.value.fog = new Fog(BG, 12, 24)
scene.value.environmentIntensity = 0.55

const camera = shallowRef<PerspectiveCamera>()
const fighterA = shallowRef<InstanceType<typeof BattleFighter>>()
const fighterB = shallowRef<InstanceType<typeof BattleFighter>>()
const shadowA = shallowRef<Mesh>()
const shadowB = shallowRef<Mesh>()
const flashLight = shallowRef<PointLight>()

const poseA = createPose()
const poseB = createPose()
const parts = createPartPoses()
let director = new CameraDirector(props.choreo, props.calm)
const sparks = new SparkPool()
const sparkRng = seededStream(props.report.seed ?? props.report.id, 7)

// Kontaktschatten als kleines Gitter, das jedes Frame auf die Bowl gelegt wird
const shadowTexture = makeShadowTexture()
const SHADOW_SIZE = BEY_SIZE * 1.4
function makeShadowGeometry() {
  const g = new PlaneGeometry(SHADOW_SIZE, SHADOW_SIZE, 6, 6)
  g.rotateX(-Math.PI / 2)
  return g
}
const shadowGeometries = [makeShadowGeometry(), makeShadowGeometry()] as const
const shadowBase = Float32Array.from(shadowGeometries[0].attributes.position!.array)

function groundAt(x: number, z: number) {
  const r = Math.hypot(x, z)
  return r > arena.radius ? pocketFloorY(arena) : bowlY(arena, r)
}

function placeShadow(mesh: Mesh | undefined, pose: FighterPose, burstFade: number) {
  if (!mesh) return
  const pos = mesh.geometry.attributes.position as BufferAttribute
  const arr = pos.array as Float32Array
  for (let i = 0; i < pos.count; i++) {
    const x = pose.x + shadowBase[i * 3]!
    const z = pose.z + shadowBase[i * 3 + 2]!
    arr[i * 3] = x
    arr[i * 3 + 1] = groundAt(x, z) + 0.012
    arr[i * 3 + 2] = z
  }
  pos.needsUpdate = true
  const lift = pose.y - groundAt(pose.x, pose.z)
  const mat = mesh.material as MeshBasicMaterial
  mat.opacity = Math.max(0, 1 - lift / 0.9) * burstFade
}

const canvas = () => (renderer as WebGLRenderer).domElement
let controls: OrbitControls | null = null
let manual = false
const tmpPos = new Vector3()

function setupControls(cam: PerspectiveCamera) {
  const c = new OrbitControls(cam, canvas())
  c.mouseButtons = { LEFT: MOUSE.ROTATE, MIDDLE: MOUSE.DOLLY, RIGHT: MOUSE.ROTATE }
  c.touches = { ONE: null, TWO: TOUCH.DOLLY_ROTATE }
  c.enablePan = false
  c.enableDamping = true
  c.dampingFactor = 0.08
  c.rotateSpeed = 0.6
  c.zoomSpeed = 0.6
  c.minDistance = 3.5
  c.maxDistance = 14
  c.minPolarAngle = 0.15
  c.maxPolarAngle = 1.4
  c.addEventListener('start', () => {
    manual = true
  })
  canvas().style.touchAction = 'pan-y'
  return c
}

/** Doppelklick: zurück zur Broadcast-Kamera. */
function resetView() {
  if (!camera.value || !controls) return
  manual = false
  director.takeOver(camera.value.position, controls.target)
}

onMounted(() => canvas().addEventListener('dblclick', resetView))
onUnmounted(() => {
  canvas().removeEventListener('dblclick', resetView)
  controls?.dispose()
  scene.value.fog = null
  sparks.dispose()
  shadowTexture.dispose()
  shadowGeometries.forEach((g) => g.dispose())
})

let nextClash = 0
let lastT = -Infinity
let flash = 0

// ---------------------------------------------------------------- Aufschlag nach dem Launch
function flatRing(inner: number, outer: number, color: string, opacity: number) {
  const g = new RingGeometry(inner, outer, 48)
  g.rotateX(-Math.PI / 2)
  const m = new ThreeMesh(
    g,
    // ohne Tiefentest: der flache Ring würde sonst von der gewölbten Bowl verdeckt
    new ThreeBasicMaterial({
      color,
      transparent: true,
      opacity,
      depthWrite: false,
      depthTest: false,
    }),
  )
  m.renderOrder = 3
  return m
}

// Aufschlag am Landepunkt: Funken und ein sich ausbreitender Ring
const impactRings = [flatRing(0.8, 1, '#ffffff', 0), flatRing(0.8, 1, '#ffffff', 0)]
const impactGroup = new Group()
impactGroup.add(...impactRings)
let nextLanding = 0
const IMPACT_MS = 650

function placeImpacts(c: Choreography, t: number) {
  const landings = c.landings ?? []
  while (nextLanding < landings.length && landings[nextLanding]!.t <= t) {
    const l = landings[nextLanding++]!
    if (t - l.t < 250) sparks.spawn(l.x, l.y, l.z, l.nx, l.nz, 0.45, sparkRng)
  }
  impactRings.forEach((ring, i) => {
    const l = landings[i]
    const age = l ? t - l.t : -1
    const mat = ring.material as ThreeBasicMaterial
    if (!l || age < 0 || age > IMPACT_MS) {
      mat.opacity = 0
      return
    }
    const u = age / IMPACT_MS
    ring.position.set(l.x, l.y + 0.02, l.z)
    ring.scale.setScalar(0.15 + 0.85 * (1 - (1 - u) * (1 - u)))
    mat.opacity = 0.7 * (1 - u)
  })
}

onUnmounted(() => {
  for (const m of impactRings) {
    m.geometry.dispose()
    ;(m.material as ThreeBasicMaterial).dispose()
  }
})

// Nach dem Anspinnen wechselt die Wartestellung zur echten Choreografie, die Kamera fährt ohne Sprung weiter.
watch(
  () => props.choreo,
  (c) => {
    const next = new CameraDirector(c, props.calm)
    if (camera.value && controls) next.takeOver(camera.value.position, controls.target)
    director = next
    nextClash = 0
    nextLanding = 0
    lastT = -Infinity
  },
)

/**
 * Aussehen eines KI-Gegners hängt nur an Name und Farbe, nicht am Kampf-Seed. So sieht er vor dem Anspinnen,
 * im Kampf und im Replay gleich aus.
 */
/** Modellnamen der Teile eines Kämpfers, damit eine KI nie genauso aussieht wie ihr Gegner. */
const partKeys = (f: FighterDto | undefined) => f?.parts?.map((p) => modelKey(p.modelPath)) ?? []
const lookSeed = (f: FighterDto | undefined, salt: number) =>
  hashString(`${f?.beyblade ?? ''}|${f?.accent ?? ''}`) + salt

const { onBeforeRender } = useLoop()

onBeforeRender(({ delta }) => {
  const cam = camera.value
  if (!cam || !fighterA.value || (!props.solo && !fighterB.value)) return
  if (!controls) controls = setupControls(cam)

  const c = props.choreo
  const t = props.clock.t
  // Replay oder Zurückspulen: Effekte neu aufsetzen
  if (t < lastT - 50) {
    nextClash = 0
    nextLanding = 0
    flash = 0
    sparks.clear()
  }
  lastT = t

  c.sample('A', t, poseA)
  c.sample('B', t, poseB)
  const burst = c.sampleParts(t, parts)
  fighterA.value.apply(poseA, burst && c.loser === 'A' ? parts : null)
  fighterB.value?.apply(poseB, burst && c.loser === 'B' ? parts : null)

  placeImpacts(c, t)

  const fade = (pose: FighterPose) => (pose.burst ? Math.max(0, 1 - (t - c.finishMs) / 300) : 1)
  placeShadow(shadowA.value, poseA, fade(poseA))
  placeShadow(shadowB.value, poseB, fade(poseB))

  while (nextClash < c.clashes.length && c.clashes[nextClash]!.t <= t) {
    const clash = c.clashes[nextClash++]!
    // Nur frische Treffer zünden, beim Überspringen nicht alles auf einmal
    if (t - clash.t > 250) continue
    sparks.spawn(clash.x, clash.y, clash.z, clash.nx, clash.nz, clash.intensity, sparkRng)
    if (!props.calm) {
      flash = Math.max(flash, clash.intensity + (clash.final ? 0.6 : 0))
      flashLight.value?.position.set(clash.x, clash.y + 0.4, clash.z)
    }
  }
  sparks.update(delta)
  flash *= Math.exp(-delta / 0.08)
  if (flashLight.value) flashLight.value.intensity = flash * 30

  director.update(delta, t, poseA, poseB, c.winner === 'A' ? poseA : poseB)
  if (manual) {
    controls.update()
  } else {
    const p = director.position
    const s = director.shake
    cam.position.set(p.x + s.x, p.y + s.y, p.z + s.z)
    tmpPos.set(director.target.x, director.target.y, director.target.z)
    cam.lookAt(tmpPos)
    controls.target.copy(tmpPos)
  }
})
</script>

<template>
  <TresPerspectiveCamera ref="camera" :position="[0, 5, 10]" :fov="34" :near="0.1" :far="60" />
  <StudioEnvironment />

  <TresHemisphereLight :args="['#c9ccd6', '#050506', 0.35]" />
  <TresDirectionalLight :position="[3.5, 9, 6]" :intensity="2.4" />
  <TresDirectionalLight :position="[-4, 3.5, -7]" :intensity="1.5" color="#bcc8ff" />
  <TresPointLight ref="flashLight" color="#fff1d6" :intensity="0" :distance="7" :decay="2" />

  <BattleBowl :arena="arena" :variant="report.arena?.variant" />

  <TresMesh ref="shadowA" :geometry="shadowGeometries[0]" :render-order="1">
    <TresMeshBasicMaterial :map="shadowTexture" transparent :depth-write="false" />
  </TresMesh>
  <TresMesh v-if="!solo" ref="shadowB" :geometry="shadowGeometries[1]" :render-order="1">
    <TresMeshBasicMaterial :map="shadowTexture" transparent :depth-write="false" />
  </TresMesh>

  <BattleFighter
    ref="fighterA"
    :fighter="report.fighters?.[0]"
    :seed="lookSeed(report.fighters?.[0], 1)"
    :avoid="partKeys(report.fighters?.[1])"
  />
  <BattleFighter
    v-if="!solo"
    ref="fighterB"
    :fighter="report.fighters?.[1]"
    :seed="lookSeed(report.fighters?.[1], 2)"
    :avoid="partKeys(report.fighters?.[0])"
  />

  <primitive :object="sparks.object" />
  <primitive :object="impactGroup" />
</template>
