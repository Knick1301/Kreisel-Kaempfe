<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import {
  ARENA_LIST,
  arenaById,
  arenaTraits,
  arenaVariant,
  type ArenaDefinition,
  type ArenaVariant,
} from '@/game/arenas'

/**
 * Arena-Wahl vor dem Kampf: alle Arenen als Karten, darunter Look (Variante) und Spielwerte der gewählten.
 * Pfeiltasten wechseln innerhalb einer Gruppe (Radio-Gruppen mit wanderndem Fokus).
 */
const props = withDefaults(
  defineProps<{
    arenaId: string
    /** gewählte Variante je Arena */
    variants: Record<string, string>
    disabled?: boolean
  }>(),
  { disabled: false },
)
const emit = defineEmits<{
  arena: [id: string]
  variant: [id: string]
}>()

const chosen = computed(() => arenaById(props.arenaId))
const variantOf = (a: ArenaDefinition) => arenaVariant(a, props.variants[a.id])
const chosenVariant = computed(() => variantOf(chosen.value))
const traits = computed(() => arenaTraits(chosen.value))

/** Vorschaubild aus der Palette: Boden als Verlauf, Akzent als Ring. */
function thumb(v: ArenaVariant) {
  const f = v.palette.floor
  return {
    background: `radial-gradient(circle at 50% 45%, ${f[0]} 0%, ${f[1]} 30%, ${f[3]} 62%, ${f[4]} 100%)`,
    boxShadow: `inset 0 0 0 2px ${v.palette.accent}, inset 0 0 14px ${v.palette.accent}66`,
  }
}
function swatch(v: ArenaVariant) {
  const f = v.palette.floor
  return {
    background: `linear-gradient(135deg, ${f[0]} 0%, ${f[2]} 45%, ${v.palette.accent} 100%)`,
  }
}
const signed = (n: number) => (n > 0 ? `+${n} %` : n < 0 ? `−${Math.abs(n)} %` : '±0')

const arenaButtons = ref<HTMLButtonElement[]>([])
const variantButtons = ref<HTMLButtonElement[]>([])

/** Pfeiltasten in einer Radio-Gruppe: Auswahl und Fokus wandern mit. */
async function onKey(
  e: KeyboardEvent,
  ids: readonly string[],
  current: string,
  pick: (id: string) => void,
  buttons: HTMLButtonElement[],
) {
  const step =
    e.key === 'ArrowDown' || e.key === 'ArrowRight'
      ? 1
      : e.key === 'ArrowUp' || e.key === 'ArrowLeft'
        ? -1
        : 0
  if (!step || props.disabled) return
  e.preventDefault()
  const i = (ids.indexOf(current) + step + ids.length) % ids.length
  pick(ids[i]!)
  await nextTick()
  buttons[i]?.focus()
}
</script>

<template>
  <section class="panel arena-picker" aria-labelledby="arena-picker-title">
    <header class="panel-head">
      <span id="arena-picker-title" class="label"><i class="dot" />Arena</span>
      <span class="label">{{ ARENA_LIST.length }} Stadien</span>
    </header>

    <div class="panel-body">
      <div
        class="cards"
        role="radiogroup"
        aria-label="Arena wählen"
        @keydown="
          onKey(
            $event,
            ARENA_LIST.map((a) => a.id),
            chosen.id,
            (id) => emit('arena', id),
            arenaButtons,
          )
        "
      >
        <button
          v-for="a in ARENA_LIST"
          :key="a.id"
          ref="arenaButtons"
          type="button"
          class="card"
          role="radio"
          :aria-checked="a.id === chosen.id"
          :tabindex="a.id === chosen.id ? 0 : -1"
          :class="{ active: a.id === chosen.id }"
          :disabled="disabled"
          @click="emit('arena', a.id)"
        >
          <span class="thumb" :style="thumb(variantOf(a))" aria-hidden="true" />
          <span class="card-text">
            <strong>{{ a.name }}</strong>
            <span class="card-desc">{{ a.description }}</span>
          </span>
        </button>
      </div>

      <div class="block">
        <span class="label">Look</span>
        <div
          class="looks"
          role="radiogroup"
          :aria-label="`Look für ${chosen.name}`"
          @keydown="
            onKey(
              $event,
              chosen.variants.map((v) => v.id),
              chosenVariant.id,
              (id) => emit('variant', id),
              variantButtons,
            )
          "
        >
          <button
            v-for="v in chosen.variants"
            :key="v.id"
            ref="variantButtons"
            type="button"
            class="look"
            role="radio"
            :aria-checked="v.id === chosenVariant.id"
            :tabindex="v.id === chosenVariant.id ? 0 : -1"
            :class="{ active: v.id === chosenVariant.id }"
            :disabled="disabled"
            @click="emit('variant', v.id)"
          >
            <span class="swatch" :style="swatch(v)" aria-hidden="true" />
            <span>{{ v.name }}</span>
          </button>
        </div>
      </div>

      <div class="block">
        <span class="label">Spielgefühl</span>
        <dl class="traits">
          <div v-for="t in traits" :key="t.label" class="trait">
            <dt>{{ t.label }}</dt>
            <dd>
              <span class="pips" role="img" :aria-label="`${t.level} von 5`">
                <i v-for="n in 5" :key="n" :class="{ on: n <= t.level, mid: n === 3 }" />
              </span>
              <span class="mono pct">{{ signed(t.percent) }}</span>
            </dd>
          </div>
        </dl>
        <p class="note">Werte im Vergleich zum Standard-Stadion.</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.cards {
  display: grid;
  gap: 6px;
}

.card {
  display: grid;
  grid-template-columns: 40px 1fr;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 7px 10px 7px 8px;
  border: 1px solid var(--line);
  border-radius: 3px;
  background: none;
  color: var(--text-muted);
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.15s,
    background 0.15s,
    color 0.15s;
}

.card:hover:not(:disabled) {
  border-color: var(--line-strong);
  color: var(--text);
}

.card.active {
  border-color: var(--text);
  background: var(--surface-raised);
  color: var(--text);
}

.card:focus-visible,
.look:focus-visible {
  outline: 2px solid var(--text);
  outline-offset: 2px;
}

.card:disabled,
.look:disabled {
  cursor: default;
  opacity: 0.6;
}

.thumb {
  width: 40px;
  height: 40px;
  border-radius: 50%;
}

.card-text {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.card-text strong {
  font-size: 0.95rem;
  font-weight: 600;
}

.card-desc {
  display: -webkit-box;
  overflow: hidden;
  font-size: 0.76rem;
  line-height: 1.35;
  color: var(--text-faint);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
}

/* nur die gewählte Karte zeigt die ganze Beschreibung */
.card.active .card-desc {
  color: var(--text-muted);
  -webkit-line-clamp: 3;
}

.block {
  display: grid;
  gap: 8px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}

.looks {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.look {
  display: grid;
  gap: 8px;
  padding: 8px;
  border: 1px solid var(--line);
  border-radius: 3px;
  background: none;
  font-size: 0.82rem;
  color: var(--text-muted);
  text-align: left;
  cursor: pointer;
}

.look.active {
  border-color: var(--text);
  color: var(--text);
}

.swatch {
  height: 26px;
  border-radius: 2px;
}

.traits {
  display: grid;
  gap: 6px;
  margin: 0;
}

.trait {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  font-size: 0.82rem;
}

.trait dt {
  color: var(--text-muted);
}

.trait dd {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0;
}

.pips {
  display: inline-flex;
  gap: 3px;
}

.pips i {
  width: 14px;
  height: 6px;
  background: var(--line);
}

.pips i.on {
  background: var(--text);
}

.pips i.mid:not(.on) {
  background: var(--line-strong);
}

.pct {
  min-width: 48px;
  font-size: 0.74rem;
  color: var(--text-faint);
  text-align: right;
}

.note {
  margin: 2px 0 0;
  font-size: 0.72rem;
  color: var(--text-faint);
}

@media (prefers-reduced-motion: reduce) {
  .card {
    transition: none;
  }
}
</style>
