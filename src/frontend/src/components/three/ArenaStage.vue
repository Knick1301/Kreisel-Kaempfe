<script setup lang="ts">
import { TresCanvas } from '@tresjs/core'
import type { PartDto } from '@/api'
import ArenaScene from './ArenaScene.vue'

withDefaults(
  defineProps<{
    urls: string[] | null | undefined
    spinKey: number
    parts?: PartDto[]
    procedural?: boolean
    exploded?: boolean
  }>(),
  { parts: () => [], procedural: false, exploded: false },
)
</script>

<template>
  <div class="arena-stage" @contextmenu.prevent>
    <TresCanvas alpha :clear-alpha="0" :dpr="[1, 1.5]">
      <ArenaScene
        :urls="urls"
        :spin-key="spinKey"
        :parts="parts"
        :procedural="procedural"
        :exploded="exploded"
      />
    </TresCanvas>
    <p class="controls-hint" aria-hidden="true">
      <span><kbd>Links</kbd> drehen</span>
      <span><kbd>Rechts</kbd> + ziehen umsehen</span>
      <span><kbd>Rad</kbd> zoomen</span>
      <span><kbd>2×</kbd> zurücksetzen</span>
    </p>
  </div>
</template>

<style scoped>
.arena-stage {
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
