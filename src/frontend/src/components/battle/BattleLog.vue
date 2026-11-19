<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import type { BattleReport, Side } from '@/api'
import { describeEvent, formatClock } from '@/game/playback'

/** Textbasierter Kampfverlauf (ohne WebGL oder bei reduzierter Bewegung). */
const props = defineProps<{ report: BattleReport; time: number; names: Record<Side, string> }>()

const lines = computed(() => {
  const events = props.report.timeline?.events ?? []
  const out: { t: number; text: string; key: number }[] = []
  events.forEach((e, i) => {
    if (e.t > props.time || e.type === 'TORNADO') return
    const text = describeEvent(events, i, props.names)
    if (text) out.push({ t: e.t, text, key: i })
  })
  return out
})

const list = ref<HTMLElement>()
watch(
  () => lines.value.length,
  async () => {
    await nextTick()
    list.value?.lastElementChild?.scrollIntoView?.({ block: 'nearest' })
  },
)
</script>

<template>
  <div class="battle-log">
    <ol ref="list" aria-live="polite">
      <li v-for="l in lines" :key="l.key">
        <span class="mono faint">{{ formatClock(l.t) }}</span>
        <span>{{ l.text }}</span>
      </li>
    </ol>
  </div>
</template>

<style scoped>
.battle-log {
  display: grid;
  place-items: center;
  padding: 24px;
}

ol {
  width: min(520px, 100%);
  max-height: 100%;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  list-style: none;
  scrollbar-width: thin;
}

li {
  display: grid;
  grid-template-columns: 3.2em 1fr;
  gap: 12px;
  padding: 9px 0;
  border-bottom: 1px solid var(--line);
  font-size: 0.93rem;
}
</style>
