<script setup lang="ts">
import { shallowRef } from 'vue'
import { TresCanvas } from '@tresjs/core'
import type { BattleReport, Side } from '@/api'
import HitMarks from '@/components/battle/HitMarks.vue'
import type { Choreography } from '@/game/choreographer'
import type { BattleClock } from '@/game/clock'
import BattleScene from './BattleScene.vue'

defineProps<{
  report: BattleReport
  choreo: Choreography
  clock: BattleClock
  calm: boolean
  /** nur der eigene Kreisel (Vorschau vor dem Kampf) */
  solo?: boolean
  /** eigene Seite: färbt die Treffer-Anzeigen (gut/schlecht) */
  me?: Side | null
}>()

const marks = shallowRef<InstanceType<typeof HitMarks>>()
</script>

<template>
  <div class="battle-stage" @contextmenu.prevent>
    <TresCanvas alpha :clear-alpha="0" :dpr="[1, 1.5]">
      <BattleScene
        :report="report"
        :choreo="choreo"
        :clock="clock"
        :calm="calm"
        :solo="solo ?? false"
        @hit="(c, x, y) => marks?.add(c, x, y)"
      />
    </TresCanvas>
    <HitMarks v-if="!solo" ref="marks" :report="report" :me="me ?? null" :calm="calm" />
    <p class="controls-hint" aria-hidden="true">
      <span><kbd>Ziehen</kbd> umsehen</span>
      <span><kbd>Rad</kbd> zoomen</span>
      <span><kbd>2×</kbd> Broadcast-Kamera</span>
    </p>
  </div>
</template>

<style scoped>
.battle-stage {
  position: absolute;
  inset: 0;
}

.controls-hint {
  position: absolute;
  right: 28px;
  bottom: 24px;
  display: flex;
  gap: 14px;
  margin: 0;
  padding: 6px 10px;
  border-radius: 3px;
  background: var(--bg);
  font-family: var(--font-mono);
  font-size: 0.68rem;
  color: var(--text-faint);
  pointer-events: none;
}

kbd {
  margin-right: 4px;
  padding: 1px 5px;
  border: 1px solid var(--line-strong);
  border-radius: 2px;
  font: inherit;
  color: var(--text-muted);
}

@media (max-width: 1280px), (hover: none) {
  .controls-hint {
    display: none;
  }
}
</style>
