<script setup lang="ts">
import { onUnmounted, ref } from 'vue'
import type { BattleReport, Side } from '@/api'
import type { ClashMark } from '@/game/choreographer'
import { energyAt } from '@/game/playback'
import { hitKind, HIT_LABEL, type HitKind } from '@/game/hitKind'

/**
 * Treffer-Anzeigen direkt an der Trefferstelle: „Kritisch!“, „Treffer“, „Abgewehrt!“ …, dazu der Spinverlust
 * des Getroffenen. Steigt kurz auf und verblasst. Positionen kommen als Anteil der Bühne (0–1) aus der Szene.
 */
const props = withDefaults(
  defineProps<{ report: BattleReport; me?: Side | null; calm?: boolean }>(),
  { me: null, calm: false },
)

interface Mark {
  id: number
  x: number
  y: number
  kind: HitKind
  label: string
  damage: string | null
  /** gut für mich (ich treffe / wehre ab) oder schlecht */
  tone: 'good' | 'bad' | 'neutral'
}

const marks = ref<Mark[]>([])
let id = 0
const timers = new Set<ReturnType<typeof setTimeout>>()

/** Spinverlust des Getroffenen durch diesen Treffer, in Prozentpunkten. */
function damageOf(clash: ClashMark): number | null {
  const tl = props.report.timeline
  if (!tl || clash.deflected) return null
  const victim: Side = clash.attacker === 'A' ? 'B' : 'A'
  const before = energyAt(tl, victim, clash.t - tl.sampleMs)
  const after = energyAt(tl, victim, clash.t + tl.sampleMs)
  const d = Math.round((before - after) * 100)
  return d >= 1 ? d : null
}

function add(clash: ClashMark, x: number, y: number) {
  if (x < 0 || x > 1 || y < 0 || y > 1) return
  const kind = hitKind(clash)
  const damage = damageOf(clash)
  const mine = props.me
  const good = clash.deflected ? clash.attacker !== mine : clash.attacker === mine
  const mark: Mark = {
    id: ++id,
    x,
    y,
    kind,
    label: HIT_LABEL[kind],
    damage: damage ? `−${damage} %` : null,
    tone: mine ? (good ? 'good' : 'bad') : 'neutral',
  }
  marks.value = [...marks.value.slice(-5), mark]
  const timer = setTimeout(
    () => {
      marks.value = marks.value.filter((m) => m.id !== mark.id)
      timers.delete(timer)
    },
    kind === 'critical' || kind === 'smash' ? 1300 : 950,
  )
  timers.add(timer)
}

function clear() {
  marks.value = []
}

onUnmounted(() => timers.forEach((t) => clearTimeout(t)))

defineExpose({ add, clear })
</script>

<template>
  <div class="hit-marks" :class="{ calm }" aria-hidden="true">
    <span
      v-for="m in marks"
      :key="m.id"
      class="mark"
      :class="[m.kind, m.tone]"
      :style="{ left: `${m.x * 100}%`, top: `${m.y * 100}%` }"
    >
      <strong>{{ m.label }}</strong>
      <small v-if="m.damage" class="mono">{{ m.damage }}</small>
    </span>
  </div>
</template>

<style scoped>
.hit-marks {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.mark {
  position: absolute;
  display: grid;
  justify-items: center;
  gap: 1px;
  white-space: nowrap;
  transform: translate(-50%, -100%);
  animation: rise 0.95s cubic-bezier(0.2, 0.8, 0.3, 1) forwards;
  text-shadow:
    0 2px 0 rgba(0, 0, 0, 0.85),
    0 0 10px rgba(0, 0, 0, 0.6);
}

.mark strong {
  font-size: 1.05rem;
  font-weight: 800;
  font-style: italic;
  letter-spacing: -0.01em;
  color: #fff;
}

.mark small {
  font-size: 0.78rem;
  font-weight: 700;
  color: #fff;
}

/* Streifer: klein und leise */
.mark.graze strong {
  font-size: 0.8rem;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.7);
}

/* Kritisch: groß, orange, mit Stoß */
.mark.critical {
  animation:
    pop 0.22s cubic-bezier(0.2, 1.6, 0.3, 1),
    rise 1.3s cubic-bezier(0.2, 0.8, 0.3, 1) forwards;
}

.mark.critical strong {
  font-size: 1.6rem;
  color: #ffb02e;
  text-shadow:
    0 2px 0 #7a2a00,
    0 0 16px rgba(255, 140, 30, 0.85);
}

.mark.critical small {
  color: #ffd27a;
}

/* Vernichtungsschlag: noch größer, rot */
.mark.smash {
  animation:
    pop 0.25s cubic-bezier(0.2, 1.8, 0.3, 1),
    rise 1.3s cubic-bezier(0.2, 0.8, 0.3, 1) forwards;
}

.mark.smash strong {
  font-size: 2rem;
  color: #ff4a3a;
  text-shadow:
    0 3px 0 #5a0000,
    0 0 22px rgba(255, 60, 40, 0.9);
}

.mark.smash small {
  font-size: 0.95rem;
  color: #ffb3a8;
}

/* Abgewehrt: blau */
.mark.blocked strong {
  color: #8fd0ff;
  text-shadow:
    0 2px 0 #0b2a45,
    0 0 12px rgba(90, 180, 255, 0.8);
}

/* Schaden bei mir rot, beim Gegner hell */
.mark.bad small {
  color: #ff7a6e;
}

@keyframes rise {
  0% {
    opacity: 0;
    transform: translate(-50%, -60%);
  }
  12% {
    opacity: 1;
    transform: translate(-50%, -100%);
  }
  70% {
    opacity: 1;
  }
  100% {
    opacity: 0;
    transform: translate(-50%, -190%);
  }
}

@keyframes pop {
  from {
    scale: 1.8;
  }
  to {
    scale: 1;
  }
}

.calm .mark,
.calm .mark.critical,
.calm .mark.smash {
  animation: fade 0.9s ease-out forwards;
}

@keyframes fade {
  0%,
  70% {
    opacity: 1;
  }
  100% {
    opacity: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .mark,
  .mark.critical,
  .mark.smash {
    animation: fade 0.9s ease-out forwards;
  }
}
</style>
