<script setup lang="ts">
import { computed, onUnmounted, shallowRef } from 'vue'
import { Euler, Quaternion, Vector3, type Group, type Material } from 'three'
import type { FighterDto } from '@/api'
import { BEY_SIZE } from '@/game/arenas'
import type { FighterPose, PartPose } from '@/game/choreographer'
import { botDesigns, designsFor } from '@/game/partDesigns'
import { useModelUrls } from '@/composables/useModelUrls'
import BeybladeRotor from '../BeybladeRotor.vue'
import ProceduralRotor from '../ProceduralRotor.vue'
import { makeMetallic } from '../materials'
import type { RotorPart } from '../rotorParts'

/** Ein Kämpfer in der Arena: GLB-Modell, wenn alle drei Dateien da sind, sonst das prozedurale Modell. */
const props = defineProps<{ fighter: FighterDto | undefined; seed: number }>()

const urls = useModelUrls(() => props.fighter?.parts ?? [])
const designs = computed(() => {
  const f = props.fighter
  if (f?.parts?.length === 3) return designsFor(f.parts)
  const stats = f?.stats ?? {
    attack: 4,
    defense: 6,
    stamina: 10,
    dash: 5,
    weight: 78,
    burstResistance: 3,
  }
  return botDesigns(stats, f?.accent, props.seed)
})
const outer = shallowRef<Group>()
const inner = shallowRef<Group>()
const rotor = shallowRef<InstanceType<typeof BeybladeRotor>>()
const procedural = shallowRef<InstanceType<typeof ProceduralRotor>>()

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
  if (procedural.value) return { parts: procedural.value.parts(), scale: procedural.value.scale() }
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
      <ProceduralRotor v-else-if="urls === null" ref="procedural" :designs="designs" />
    </TresGroup>
  </TresGroup>
</template>
