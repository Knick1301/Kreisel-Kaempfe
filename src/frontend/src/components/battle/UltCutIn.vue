<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { ULT_LABEL, type FighterDto, type UltType } from '@/api'
import { ultPowerLabel } from '@/game/playback'
import { ULT_COLOR } from '@/game/ult'
import BeyEmblem from './BeyEmblem.vue'

/**
 * Anime-Einblendung beim Auslösen einer Ult: Blitz, Abdunkeln, Speedlines, ein schräges Band in der Farbe der
 * Ult mit großem Emblem und Namen. Eigene Ult kommt von links, gegnerische von rechts. Meldet `done` nach
 * `durationMs`, der Kampf steht so lange still.
 */
const props = withDefaults(
  defineProps<{
    fighter: FighterDto | undefined
    ult: UltType
    mine: boolean
    /** Stärke der Ult (vom Anspinnen), null wenn noch unbekannt */
    power?: number | null
    durationMs?: number
    /** ruhige Version: nur Band, keine Blitze und Speedlines */
    calm?: boolean
  }>(),
  { power: null, durationMs: 1500, calm: false },
)
const emit = defineEmits<{ done: [] }>()

const color = computed(() => ULT_COLOR[props.ult])
const powerLabel = computed(() => (props.power == null ? null : ultPowerLabel(props.power)))

let timer: ReturnType<typeof setTimeout> | undefined
onMounted(() => {
  timer = setTimeout(() => emit('done'), props.durationMs)
})
onUnmounted(() => clearTimeout(timer))
</script>

<template>
  <div
    class="cut-in"
    :class="{ mine, theirs: !mine, calm }"
    :style="{ '--ult': color, '--dur': `${durationMs}ms` }"
    role="status"
    aria-live="assertive"
  >
    <div class="shade" />
    <div v-if="!calm" class="speedlines" />
    <div v-if="!calm" class="flash" />

    <div class="band">
      <div class="streaks" />
      <div class="content">
        <svg class="emblem" viewBox="-50 -50 100 100" aria-hidden="true">
          <circle r="47" class="halo" />
          <g class="spin"><BeyEmblem :fighter="fighter" /></g>
        </svg>
        <div class="text">
          <span class="who mono"
            >{{ mine ? 'Deine Ult' : 'Gegner-Ult' }} · {{ fighter?.beyblade }}</span
          >
          <strong class="title">{{ ULT_LABEL[ult] }}</strong>
          <span v-if="powerLabel" class="power mono">{{ powerLabel }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cut-in {
  position: absolute;
  inset: 0;
  z-index: 20;
  overflow: hidden;
  pointer-events: none;
}

.shade {
  position: absolute;
  inset: 0;
  background: radial-gradient(ellipse at center, rgba(0, 0, 0, 0.35), rgba(0, 0, 0, 0.88));
  animation: shade var(--dur) ease-out forwards;
}

@keyframes shade {
  0% {
    opacity: 0;
  }
  10%,
  80% {
    opacity: 1;
  }
  100% {
    opacity: 0;
  }
}

/* Speedlines: Strahlen um die Mitte, die Mitte bleibt frei */
.speedlines {
  position: absolute;
  inset: -50%;
  background: repeating-conic-gradient(
    from 0deg,
    transparent 0deg 4deg,
    color-mix(in srgb, var(--ult) 55%, white) 4deg 4.6deg,
    transparent 4.6deg 9deg
  );
  mask-image: radial-gradient(circle at center, transparent 18%, black 42%);
  -webkit-mask-image: radial-gradient(circle at center, transparent 18%, black 42%);
  animation:
    lines-spin 0.6s linear infinite,
    lines-fade var(--dur) ease-out forwards;
}

@keyframes lines-spin {
  to {
    transform: rotate(9deg);
  }
}

@keyframes lines-fade {
  0% {
    opacity: 0;
  }
  8%,
  75% {
    opacity: 0.7;
  }
  100% {
    opacity: 0;
  }
}

.flash {
  position: absolute;
  inset: 0;
  background: white;
  animation: flash 0.32s ease-out forwards;
}

@keyframes flash {
  0% {
    opacity: 0.95;
  }
  100% {
    opacity: 0;
  }
}

/* Das Band: schräger Streifen über die ganze Breite */
.band {
  position: absolute;
  top: 50%;
  left: -10%;
  width: 120%;
  height: clamp(150px, 26vh, 240px);
  margin-top: calc(clamp(150px, 26vh, 240px) / -2);
  background: linear-gradient(
    90deg,
    color-mix(in srgb, var(--ult) 85%, black),
    color-mix(in srgb, var(--ult) 40%, black) 55%,
    rgba(0, 0, 0, 0.92)
  );
  border-block: 4px solid color-mix(in srgb, var(--ult) 60%, white);
  box-shadow:
    0 0 40px var(--ult),
    0 0 120px color-mix(in srgb, var(--ult) 50%, transparent);
  transform: rotate(-8deg);
  overflow: hidden;
}

.theirs .band {
  background: linear-gradient(
    270deg,
    color-mix(in srgb, var(--ult) 85%, black),
    color-mix(in srgb, var(--ult) 40%, black) 55%,
    rgba(0, 0, 0, 0.92)
  );
  transform: rotate(8deg);
}

.mine .band {
  animation: band-in-left var(--dur) cubic-bezier(0.2, 0.9, 0.2, 1) forwards;
}

.theirs .band {
  animation: band-in-right var(--dur) cubic-bezier(0.2, 0.9, 0.2, 1) forwards;
}

@keyframes band-in-left {
  0% {
    transform: rotate(-8deg) translateX(-110%);
  }
  14% {
    transform: rotate(-8deg) translateX(3%);
  }
  20%,
  80% {
    transform: rotate(-8deg) translateX(0);
  }
  100% {
    transform: rotate(-8deg) translateX(110%);
  }
}

@keyframes band-in-right {
  0% {
    transform: rotate(8deg) translateX(110%);
  }
  14% {
    transform: rotate(8deg) translateX(-3%);
  }
  20%,
  80% {
    transform: rotate(8deg) translateX(0);
  }
  100% {
    transform: rotate(8deg) translateX(-110%);
  }
}

/* Bewegungsstreifen im Band */
.streaks {
  position: absolute;
  inset: 0;
  background: repeating-linear-gradient(
    90deg,
    transparent 0 60px,
    rgba(255, 255, 255, 0.16) 60px 64px,
    transparent 64px 140px
  );
  animation: streaks 0.25s linear infinite;
}

.theirs .streaks {
  animation-direction: reverse;
}

@keyframes streaks {
  to {
    background-position: 140px 0;
  }
}

.content {
  position: relative;
  display: flex;
  align-items: center;
  gap: clamp(16px, 3vw, 40px);
  height: 100%;
  padding-inline: 14%;
}

.theirs .content {
  flex-direction: row-reverse;
  text-align: right;
}

.emblem {
  flex: none;
  width: clamp(120px, 20vh, 200px);
  height: clamp(120px, 20vh, 200px);
  overflow: visible;
  animation: emblem-punch var(--dur) cubic-bezier(0.2, 1.4, 0.3, 1) forwards;
}

.halo {
  fill: none;
  stroke: white;
  stroke-width: 2;
  filter: drop-shadow(0 0 10px var(--ult));
}

.spin {
  animation: spin 0.35s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes emblem-punch {
  0%,
  8% {
    transform: scale(0.2);
  }
  22% {
    transform: scale(1.12);
  }
  30%,
  100% {
    transform: scale(1);
  }
}

.text {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.who {
  font-size: clamp(0.7rem, 1.4vh, 0.9rem);
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.85);
}

.title {
  font-size: clamp(2.2rem, 7.5vh, 5.2rem);
  font-weight: 800;
  font-style: italic;
  line-height: 0.95;
  letter-spacing: -0.03em;
  text-transform: uppercase;
  color: white;
  -webkit-text-stroke: 2px color-mix(in srgb, var(--ult) 70%, black);
  text-shadow:
    0 0 18px var(--ult),
    4px 4px 0 color-mix(in srgb, var(--ult) 60%, black);
  white-space: nowrap;
  animation: title-slam var(--dur) cubic-bezier(0.2, 1.3, 0.3, 1) forwards;
}

@keyframes title-slam {
  0%,
  12% {
    opacity: 0;
    transform: scale(2.4) skewX(-12deg);
  }
  24% {
    opacity: 1;
    transform: scale(0.96) skewX(-12deg);
  }
  32%,
  100% {
    opacity: 1;
    transform: scale(1) skewX(-12deg);
  }
}

.power {
  justify-self: start;
  padding: 2px 10px;
  border: 1px solid white;
  font-size: clamp(0.75rem, 1.6vh, 1rem);
  letter-spacing: 0.25em;
  text-transform: uppercase;
  color: white;
  background: color-mix(in srgb, var(--ult) 50%, transparent);
}

.theirs .power {
  justify-self: end;
}

/* Ruhige Version: nur Band einblenden, keine Bewegung */
.calm .band,
.calm .shade,
.calm .emblem,
.calm .title,
.calm .spin,
.calm .streaks {
  animation: none;
}

.calm .band {
  transform: rotate(0);
}

@media (prefers-reduced-motion: reduce) {
  .band,
  .emblem,
  .title,
  .spin,
  .streaks,
  .speedlines {
    animation: none !important;
  }

  .band {
    transform: none;
  }
}
</style>
