<script setup lang="ts">
import { ref } from 'vue'


const spinning = ref(true)

function spin() {
  spinning.value = true
}

const blades = [0, 60, 120, 180, 240, 300]
const grooves = Array.from({ length: 12 }, (_, i) => i * 30)
</script>

<template>
  <div class="spinner" :class="{ spinning }" @mouseenter="spin">
    <div class="glow" />
    <div class="wobble">
      <svg class="bey" viewBox="-100 -100 200 200" aria-hidden="true">
        <defs>
          <linearGradient id="bey-metal" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stop-color="#f4f7fb" />
            <stop offset="0.45" stop-color="#9aa7bb" />
            <stop offset="0.6" stop-color="#e6ebf3" />
            <stop offset="1" stop-color="#5d6a80" />
          </linearGradient>
          <linearGradient id="bey-blue" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stop-color="#5b9bff" />
            <stop offset="0.5" stop-color="#1c43c0" />
            <stop offset="1" stop-color="#0a1d63" />
          </linearGradient>
          <linearGradient id="bey-gold" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stop-color="#ffe08a" />
            <stop offset="1" stop-color="#b9821b" />
          </linearGradient>
          <radialGradient id="bey-face" cx="0.4" cy="0.35" r="0.8">
            <stop offset="0" stop-color="#2556b8" />
            <stop offset="1" stop-color="#061230" />
          </radialGradient>
        </defs>

        <g class="rotor" @animationend="spinning = false">
          <g v-for="a in blades" :key="`b${a}`" :transform="`rotate(${a})`">
            <path
              d="M -23.2 -49.8 Q 0 -92 34.8 -86.2 Q 24 -70 29 -50.2 Z"
              fill="url(#bey-blue)"
              stroke="url(#bey-metal)"
              stroke-width="2.5"
              stroke-linejoin="round"
            />
            <path d="M -12 -62 Q 6 -86 30 -84 Q 8 -77 -4 -60 Z" fill="url(#bey-metal)" />
          </g>

          <circle r="58" fill="url(#bey-metal)" stroke="#22314d" stroke-width="1.5" />

          <path
            v-for="a in blades"
            :key="`g${a}`"
            :transform="`rotate(${a + 30})`"
            d="M -8 -57 L 8 -57 L 4.5 -46 L -4.5 -46 Z"
            fill="url(#bey-gold)"
          />

          <circle r="44" fill="url(#bey-blue)" stroke="#0a1840" stroke-width="1.5" />
          <line
            v-for="a in grooves"
            :key="`l${a}`"
            :transform="`rotate(${a})`"
            x1="0"
            y1="-35"
            x2="0"
            y2="-44"
            stroke="#091a4d"
            stroke-width="2"
          />

          <circle r="31" fill="url(#bey-metal)" />
          <circle r="25" fill="url(#bey-face)" stroke="url(#bey-gold)" stroke-width="1.8" />
          <path
            d="M 0 -17 L 4 -4 L 17 0 L 4 4 L 0 17 L -4 4 L -17 0 L -4 -4 Z"
            fill="#dbe8ff"
            opacity="0.92"
          />
          <path
            d="M 0 -9 L 2 -2 L 9 0 L 2 2 L 0 9 L -2 2 L -9 0 L -2 -2 Z"
            transform="rotate(45)"
            fill="url(#bey-gold)"
          />
          <circle r="3" fill="#061230" />
        </g>
      </svg>
    </div>
    <div class="shadow" />
  </div>
</template>

<style scoped>
.spinner {
  position: relative;
  display: grid;
  place-items: center;
  aspect-ratio: 1;
  width: 100%;
}

.glow {
  position: absolute;
  inset: 8%;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(59, 130, 246, 0.45), transparent 65%);
  filter: blur(14px);
  opacity: 0.6;
  transition: opacity 0.6s;
}

.wobble {
  position: relative;
  width: 88%;
}

.bey {
  display: block;
  width: 100%;
  filter: drop-shadow(0 6px 10px rgba(0, 0, 0, 0.6));
}

.rotor {
  transform-box: fill-box;
  transform-origin: center;
}

.spinning .rotor {
  animation: spin 2.6s cubic-bezier(0.25, 0.6, 0.3, 1);
}

.spinning .wobble {
  animation: wobble 1.3s ease-in-out 2;
}

.spinning .glow {
  opacity: 1;
}

.shadow {
  position: absolute;
  bottom: 0;
  width: 55%;
  height: 8%;
  border-radius: 50%;
  background: radial-gradient(rgba(0, 0, 0, 0.55), transparent 70%);
}

@keyframes spin {
  to {
    transform: rotate(720deg);
  }
}

@keyframes wobble {
  0%,
  100% {
    transform: translate(0, 0);
  }
  25% {
    transform: translate(2%, -1.5%);
  }
  50% {
    transform: translate(0, -2.5%);
  }
  75% {
    transform: translate(-2%, -1.5%);
  }
}

@media (prefers-reduced-motion: reduce) {
  .spinning .wobble,
  .spinning .rotor {
    animation: none;
  }
}
</style>
