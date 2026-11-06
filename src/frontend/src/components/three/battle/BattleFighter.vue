<script setup lang="ts">
import { onUnmounted, shallowRef } from 'vue'
import { Euler, Quaternion, Vector3, type Group, type Material } from 'three'
import type { FighterDto } from '@/api'
import { BEY_SIZE } from '@/game/arenas'
import type { FighterPose, PartPose } from '@/game/choreographer'
import { useModelUrls } from '@/composables/useModelUrls'
import BeybladeRotor from '../BeybladeRotor.vue'
import PlaceholderRotor from '../PlaceholderRotor.vue'
import { makeMetallic } from '../materials'
import type { RotorPart } from '../rotorParts'

/** Ein Kämpfer in der Arena: echtes Modell, wenn alle GLBs da sind, sonst der Platzhalter. */
const props = defineProps<{ fighter: FighterDto | undefined }>()

const PLACEHOLDER_SCALE = BEY_SIZE / 1.9

const urls = useModelUrls(() => props.fighter?.parts ?? [])
const outer = shallowRef<Group>()
const inner = shallowRef<Group>()
const rotor = shallowRef<InstanceType<typeof BeybladeRotor>>()
const placeholder = shallowRef<InstanceType<typeof PlaceholderRotor>>()

let materials: Material[] = []
function onReady() {
  materials = makeMetallic(rotor.value?.root())
}
onUnmounted(() => materials.forEach((m) => m.dispose()))

// vorab angelegt, damit `apply` im Render-Loop nichts alloziert
const euler = new Euler()
const q = new Quaternion()
const spinInverse = new Quaternion()
const offset = new Vector3()
const Y_AXIS = new Vector3(0, 1, 0)
let displaced = false

function rotorParts(): { parts: RotorPart[]; scale: number } {
  if (rotor.value) return { parts: rotor.value.parts(), scale: rotor.value.scale() }
  if (placeholder.value) return { parts: placeholder.value.parts(), scale: PLACEHOLDER_SCALE }
  return { parts: [], scale: 1 }
}

function apply(pose: FighterPose, parts: PartPose[] | null) {
  const o = outer.value
  const i = inner.value
  if (!o || !i) return
  o.position.set(pose.x, pose.y, pose.z)
  i.rotation.y = pose.spin

  if (pose.burst && parts) {
    o.rotation.set(0, 0, 0)
    const { parts: list, scale } = rotorParts()
    spinInverse.setFromAxisAngle(Y_AXIS, -pose.spin)
    for (let k = 0; k < list.length; k++) {
      const part = list[k]!
      const p = parts[k]
      if (!p) continue
      euler.set(p.rx, p.ry, p.rz)
      q.setFromEuler(euler)
      offset.set(p.x, p.y, p.z).applyQuaternion(spinInverse).divideScalar(scale)
      // Drehung um die Mitte des Teils: Position = Pivot − q·(Pivot − Ruhe) + Versatz
      part.group.quaternion.copy(q)
      part.group.position
        .copy(part.pivot)
        .sub(part.rest)
        .applyQuaternion(q)
        .negate()
        .add(part.pivot)
        .add(offset)
    }
    displaced = true
    return
  }

  o.rotation.set(pose.tiltX, 0, pose.tiltZ)
  if (displaced) {
    for (const part of rotorParts().parts) {
      part.group.position.copy(part.rest)
      part.group.quaternion.identity()
    }
    displaced = false
  }
}

defineExpose({ apply })
</script>

<template>
  <TresGroup ref="outer">
    <TresGroup ref="inner">
      <BeybladeRotor
        v-if="urls"
        ref="rotor"
        :key="urls.join('|')"
        :urls="urls"
        :spin-key="0"
        :size="BEY_SIZE"
        anchor="bottom"
        :self-spin="false"
        clone
        @ready="onReady"
      />
      <TresGroup
        v-else-if="urls === null"
        :scale="PLACEHOLDER_SCALE"
        :position="[0, 0.6 * PLACEHOLDER_SCALE, 0]"
      >
        <PlaceholderRotor
          ref="placeholder"
          :spin-key="0"
          :self-spin="false"
          :accent="fighter?.accent ?? null"
        />
      </TresGroup>
    </TresGroup>
  </TresGroup>
</template>
