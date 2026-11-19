<script setup lang="ts">
import { reactive, ref } from 'vue'

/**
 * Bildschirm-Steuerung für Touch-Geräte: links ein Stick (fahren), rechts Dash und Ult.
 * Der Stick meldet die Richtung in Bildschirmkoordinaten (x nach rechts, z nach unten = zum Betrachter),
 * die Ansicht dreht sie wie WASD passend zur Kamera.
 */
const props = withDefaults(
  defineProps<{
    /** Ult ist bereit */
    ultReady?: boolean
    /** Dash wieder bereit, 0–1 */
    dashCharge?: number
  }>(),
  { ultReady: false, dashCharge: 1 },
)

const emit = defineEmits<{
  move: [x: number, z: number]
  dash: []
  ult: []
}>()

/** Weg des Sticks bis zum Anschlag in px */
const RADIUS = 60
/** Totzone, damit ein ruhender Daumen nicht schiebt */
const DEAD = 0.15

const knob = reactive({ x: 0, y: 0 })
const active = ref(false)
let pointerId: number | null = null
let originX = 0
let originY = 0

function onStickDown(e: PointerEvent) {
  if (pointerId !== null) return
  pointerId = e.pointerId
  const el = e.currentTarget as HTMLElement
  el.setPointerCapture?.(e.pointerId)
  const rect = el.getBoundingClientRect()
  originX = rect.left + rect.width / 2
  originY = rect.top + rect.height / 2
  active.value = true
  update(e)
}

function onStickMove(e: PointerEvent) {
  if (e.pointerId !== pointerId) return
  update(e)
}

function onStickUp(e: PointerEvent) {
  if (e.pointerId !== pointerId) return
  pointerId = null
  active.value = false
  knob.x = 0
  knob.y = 0
  emit('move', 0, 0)
}

function update(e: PointerEvent) {
  let dx = e.clientX - originX
  let dy = e.clientY - originY
  const d = Math.hypot(dx, dy)
  if (d > RADIUS) {
    dx = (dx / d) * RADIUS
    dy = (dy / d) * RADIUS
  }
  knob.x = dx
  knob.y = dy
  const len = Math.hypot(dx, dy)
  const mag = len / RADIUS
  if (mag < DEAD) {
    emit('move', 0, 0)
    return
  }
  // Stärke ab der Totzone neu auf 0–1 strecken
  const strength = (mag - DEAD) / (1 - DEAD)
  emit('move', (dx / len) * strength, (dy / len) * strength)
}

function onDash(e: PointerEvent) {
  e.preventDefault()
  if (props.dashCharge >= 1) emit('dash')
}

function onUlt(e: PointerEvent) {
  e.preventDefault()
  if (props.ultReady) emit('ult')
}
</script>

<template>
  <div class="touch-controls">
    <div
      class="stick"
      :class="{ active }"
      data-test="stick"
      @pointerdown="onStickDown"
      @pointermove="onStickMove"
      @pointerup="onStickUp"
      @pointercancel="onStickUp"
    >
      <span class="knob" :style="{ transform: `translate(${knob.x}px, ${knob.y}px)` }" />
    </div>
    <div class="buttons">
      <button
        type="button"
        class="btn dash"
        data-test="dash"
        :class="{ ready: dashCharge >= 1 }"
        :style="{ '--charge': Math.max(0, Math.min(1, dashCharge)) }"
        @pointerdown="onDash"
      >
        Dash
      </button>
      <button
        type="button"
        class="btn ult"
        data-test="ult"
        :class="{ ready: ultReady }"
        :disabled="!ultReady"
        @pointerdown="onUlt"
      >
        Ult
      </button>
    </div>
  </div>
</template>

<style scoped>
.touch-controls {
  position: absolute;
  inset: auto 0 0 0;
  z-index: 4;
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  padding: 0 20px 24px;
  pointer-events: none;
  touch-action: none;
  user-select: none;
  -webkit-user-select: none;
}

.stick {
  position: relative;
  width: 140px;
  height: 140px;
  border: 2px solid rgba(255, 255, 255, 0.25);
  border-radius: 50%;
  background: rgba(10, 10, 11, 0.35);
  pointer-events: auto;
  touch-action: none;
}

.stick.active {
  border-color: rgba(255, 255, 255, 0.5);
}

.knob {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 56px;
  height: 56px;
  margin: -28px 0 0 -28px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.6);
  pointer-events: none;
}

.buttons {
  display: flex;
  gap: 14px;
  align-items: flex-end;
  pointer-events: auto;
}

.btn {
  width: 76px;
  height: 76px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-radius: 50%;
  background: rgba(10, 10, 11, 0.45);
  color: var(--text-muted);
  font-weight: 600;
  font-size: 0.9rem;
  touch-action: none;
}

.btn.dash {
  background: conic-gradient(
    rgba(255, 255, 255, 0.22) calc(var(--charge) * 360deg),
    rgba(10, 10, 11, 0.45) 0
  );
}

.btn.ready {
  border-color: var(--text);
  color: var(--text);
}

.btn.ult {
  width: 92px;
  height: 92px;
}

.btn.ult.ready {
  border-color: #f5c84c;
  color: #f5c84c;
  box-shadow: 0 0 18px rgba(245, 200, 76, 0.45);
}

.btn:disabled {
  opacity: 0.5;
}
</style>
