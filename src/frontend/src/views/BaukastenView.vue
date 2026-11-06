<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, reactive, ref, watch } from 'vue'
import {
  fetchBeyblades,
  fetchParts,
  fetchPreview,
  createBeyblade,
  deleteBeyblade,
  updateBeyblade,
  type BeybladeDto,
  type BeybladeStats,
  type PartDto,
  type PartKind,
} from '@/api'
import { useRoute } from 'vue-router'
import { pickActive, setActiveId } from '@/activeBeyblade'
import { useModelUrls } from '@/composables/useModelUrls'
import { designsFor, TIP_DESIGNS, TRACK_DESIGNS, WHEEL_DESIGNS, modelKey } from '@/game/partDesigns'
import { hasWebGL } from '@/webgl'
import BeybladeSpinner from '@/components/BeybladeSpinner.vue'
import StatBar from '@/components/StatBar.vue'

const route = useRoute()

const ArenaStage = defineAsyncComponent(() => import('@/components/three/ArenaStage.vue'))

const slots: { kind: PartKind; label: string }[] = [
  { kind: 'fusion-wheels', label: 'Fusion Wheel' },
  { kind: 'spin-tracks', label: 'Spin Track' },
  { kind: 'tips', label: 'Tip' },
]

const catalog = reactive<Record<PartKind, PartDto[]>>({
  'fusion-wheels': [],
  'spin-tracks': [],
  tips: [],
})
const selected = reactive<Record<PartKind, number | null>>({
  'fusion-wheels': null,
  'spin-tracks': null,
  tips: null,
})

const collection = ref<BeybladeDto[]>([])
const saved = ref<BeybladeDto | null>(null)
const name = ref('')
const preview = ref<BeybladeStats | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const spinKey = ref(0)
const saving = ref(false)
const saveMessage = ref<string | null>(null)
const saveFailed = ref(false)

async function save() {
  const [w, s, t] = [selected['fusion-wheels'], selected['spin-tracks'], selected.tips]
  if (w == null || s == null || t == null || saving.value) return
  saving.value = true
  saveMessage.value = null
  const req = { name: name.value, fusionWheelId: w, spinTrackId: s, tipId: t }
  try {
    const result = saved.value
      ? await updateBeyblade(saved.value.id, req)
      : await createBeyblade(req)
    const i = collection.value.findIndex((b) => b.id === result.id)
    if (i >= 0) collection.value[i] = result
    else collection.value.push(result)
    saved.value = result
    setActiveId(result.id)
    name.value = result.name
    saveFailed.value = false
    saveMessage.value = `„${saved.value.name}“ gespeichert`
  } catch (e) {
    saveFailed.value = true
    saveMessage.value = e instanceof Error ? e.message : String(e)
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  try {
    const [wheels, tracks, tips, beyblades] = await Promise.all([
      fetchParts('fusion-wheels'),
      fetchParts('spin-tracks'),
      fetchParts('tips'),
      fetchBeyblades().catch(() => [] as BeybladeDto[]),
    ])
    catalog['fusion-wheels'] = wheels
    catalog['spin-tracks'] = tracks
    catalog.tips = tips
    collection.value = beyblades
    const wanted = Number(route?.query.id)
    const start =
      route?.query.neu === '1' && beyblades.length === 0
        ? null
        : (beyblades.find((b) => b.id === wanted) ?? pickActive(beyblades))
    open(start)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
})

function confirmDiscard() {
  return !saved.value || !dirty.value || window.confirm('Ungespeicherte Änderungen verwerfen?')
}

function open(b: BeybladeDto | null) {
  saved.value = b
  saveMessage.value = null
  if (b) setActiveId(b.id)
  reset()
  spinKey.value++
}

function select(b: BeybladeDto) {
  if (saved.value?.id === b.id || !confirmDiscard()) return
  open(b)
}

function startNew() {
  if (!saved.value || !confirmDiscard()) return
  open(null)
}

function nextName() {
  const taken = new Set(collection.value.map((b) => b.name))
  let n = collection.value.length + 1
  while (taken.has(`Kreisel ${n}`)) n++
  return `Kreisel ${n}`
}

const deleting = ref(false)

async function remove() {
  const b = saved.value
  if (!b || deleting.value) return
  if (!window.confirm(`„${b.name}“ wirklich löschen?`)) return
  deleting.value = true
  try {
    await deleteBeyblade(b.id)
    collection.value = collection.value.filter((x) => x.id !== b.id)
    const next = collection.value[0] ?? null
    setActiveId(next?.id ?? null)
    open(next)
    saveFailed.value = false
    saveMessage.value = `„${b.name}“ gelöscht`
  } catch (e) {
    saveFailed.value = true
    saveMessage.value = e instanceof Error ? e.message : String(e)
  } finally {
    deleting.value = false
  }
}

function reset() {
  const b = saved.value
  selected['fusion-wheels'] = b?.fusionWheel.id ?? catalog['fusion-wheels'][0]?.id ?? null
  selected['spin-tracks'] = b?.spinTrack.id ?? catalog['spin-tracks'][0]?.id ?? null
  selected.tips = b?.tip.id ?? catalog.tips[0]?.id ?? null
  name.value = b?.name ?? nextName()
}

const selectedParts = computed(() =>
  slots
    .map((s) => catalog[s.kind].find((p) => p.id === selected[s.kind]))
    .filter((p): p is PartDto => !!p),
)

const dirty = computed(() => {
  const b = saved.value
  if (!b) return true
  return (
    b.fusionWheel.id !== selected['fusion-wheels'] ||
    b.spinTrack.id !== selected['spin-tracks'] ||
    b.tip.id !== selected.tips ||
    b.name !== name.value
  )
})

watch(
  () => [selected['fusion-wheels'], selected['spin-tracks'], selected.tips] as const,
  async ([w, s, t]) => {
    if (w == null || s == null || t == null) return
    try {
      preview.value = await fetchPreview(w, s, t)
    } catch {
      preview.value = null
    }
  },
)

const urls = useModelUrls(() => selectedParts.value)
const stageUrls = ref<string[] | null | undefined>(undefined)
watch(urls, (u) => {
  if (u !== undefined) stageUrls.value = u
})

function choose(kind: PartKind, id: number) {
  if (selected[kind] === id) return
  saveMessage.value = null
  selected[kind] = id
  spinKey.value++
}

function partLine(p: PartDto) {
  return [
    p.attack && `ANG ${p.attack}`,
    p.defense && `VER ${p.defense}`,
    p.stamina && `AUS ${p.stamina}`,
  ]
    .filter(Boolean)
    .join(' · ')
}

const stats = computed(() => {
  const s = preview.value
  if (!s) return []
  const base = saved.value?.stats
  return [
    { label: 'Angriff', value: s.attack, delta: base ? s.attack - base.attack : 0 },
    { label: 'Verteidigung', value: s.defense, delta: base ? s.defense - base.defense : 0 },
    { label: 'Ausdauer', value: s.stamina, delta: base ? s.stamina - base.stamina : 0 },
  ]
})

const specs = computed(() => {
  const s = preview.value
  if (!s) return []
  const base = saved.value?.stats
  return [
    { label: 'Gewicht', value: `${s.weight}g`, delta: base ? s.weight - base.weight : 0 },
    { label: 'Dash', value: String(s.dash), delta: base ? s.dash - base.dash : 0 },
    {
      label: 'Burst-Resistenz',
      value: String(s.burstResistance),
      delta: base ? s.burstResistance - base.burstResistance : 0,
    },
  ]
})

const signed = (n: number) => (n > 0 ? `+${n}` : `−${Math.abs(n)}`)

/** Explosionsansicht: Teile schweben auseinander, damit man jedes einzeln sieht. */
const exploded = ref(false)

/** Akzentfarbe des 3D-Designs als kleiner Farbpunkt an jeder Option. */
function accentOf(kind: PartKind, p: PartDto) {
  const key = modelKey(p.modelPath)
  if (kind === 'fusion-wheels') return WHEEL_DESIGNS[key]?.accent ?? designsFor([p]).wheel.accent
  if (kind === 'spin-tracks') return TRACK_DESIGNS[key]?.accent ?? '#8b8b94'
  return TIP_DESIGNS[key]?.accent ?? '#8b8b94'
}

const webgl = hasWebGL()
</script>

<template>
  <main class="arena-page">
    <div class="stage" @click="spinKey++">
      <ArenaStage
        v-if="webgl"
        :urls="stageUrls"
        :spin-key="spinKey"
        :parts="selectedParts"
        procedural
        :exploded="exploded"
      />
      <div v-else class="stage-fallback"><BeybladeSpinner /></div>
    </div>

    <div class="overlay">
      <section class="panel build" aria-label="Kreisel zusammenbauen">
        <header class="panel-head">
          <span class="label"><i class="dot" />Kreisel-Bau</span>
          <span class="head-tools">
            <button
              v-if="webgl"
              type="button"
              class="explode"
              :aria-pressed="exploded"
              :class="{ active: exploded }"
              @click="exploded = !exploded"
            >
              {{ exploded ? 'Zusammenbauen' : 'Explosionsansicht' }}
            </button>
            <span class="label">{{ selectedParts.length }}/3 Teile</span>
          </span>
        </header>

        <div class="panel-body">
          <p v-if="error" class="hint">Backend nicht erreichbar ({{ error }})</p>
          <p v-else-if="loading" class="hint">Lade Teile …</p>

          <template v-else>
            <div class="collection">
              <span class="label">Deine Kreisel · {{ collection.length }}</span>
              <div class="chips" role="tablist" aria-label="Deine Kreisel">
                <button
                  v-for="b in collection"
                  :key="b.id"
                  type="button"
                  role="tab"
                  class="chip"
                  :class="{ active: saved?.id === b.id }"
                  :aria-selected="saved?.id === b.id"
                  :title="b.name"
                  @click="select(b)"
                >
                  {{ b.name }}
                </button>
                <button
                  type="button"
                  class="chip new"
                  :class="{ active: !saved }"
                  :aria-selected="!saved"
                  @click="startNew"
                >
                  + Neu
                </button>
              </div>
            </div>

            <div v-for="(slot, si) in slots" :key="slot.kind" class="slot">
              <div class="slot-head">
                <span class="label">{{ String(si + 1).padStart(2, '0') }} · {{ slot.label }}</span>
              </div>
              <div class="options" role="radiogroup" :aria-label="slot.label">
                <button
                  v-for="p in catalog[slot.kind]"
                  :key="p.id"
                  type="button"
                  role="radio"
                  class="option"
                  :class="{ active: selected[slot.kind] === p.id }"
                  :aria-checked="selected[slot.kind] === p.id"
                  @click="choose(slot.kind, p.id)"
                >
                  <span class="option-name">
                    <i class="swatch" :style="{ background: accentOf(slot.kind, p) }" />{{ p.name }}
                  </span>
                  <span class="option-sub">{{ partLine(p) }}</span>
                  <span class="option-weight">{{ p.weight }}g</span>
                </button>
              </div>
            </div>

            <label class="name-field">
              <span class="label">Name</span>
              <input v-model="name" type="text" maxlength="24" spellcheck="false" />
            </label>
          </template>
        </div>
      </section>

      <section class="panel values" aria-label="Werte-Vorschau">
        <header class="panel-head">
          <span class="label"><i class="dot" />Werte</span>
          <span class="label">{{ saved ? 'vs. gespeichert' : 'Vorschau' }}</span>
        </header>

        <div class="panel-body">
          <h1 class="build-name">{{ name || 'Ohne Namen' }}</h1>
          <p class="build-parts">{{ selectedParts.map((p) => p.name).join(' · ') }}</p>

          <p v-if="!preview" class="hint">Keine Vorschau verfügbar.</p>
          <template v-else>
            <div class="stats">
              <StatBar
                v-for="s in stats"
                :key="s.label"
                :label="s.label"
                :value="s.value"
                :delta="s.delta"
              />
            </div>

            <dl class="specs">
              <div v-for="s in specs" :key="s.label">
                <dt>{{ s.label }}</dt>
                <dd class="mono">
                  <span v-if="s.delta" class="spec-delta">{{ signed(s.delta) }}</span>
                  {{ s.value }}
                </dd>
              </div>
            </dl>
          </template>
        </div>
      </section>

      <div class="cta-wrap">
        <div class="actions">
          <button
            v-if="saved"
            type="button"
            class="btn-secondary danger"
            :disabled="deleting"
            @click="remove"
          >
            Löschen
          </button>
          <button type="button" class="btn-secondary" :disabled="!dirty" @click="reset">
            Zurücksetzen
          </button>
          <button
            type="button"
            class="cta"
            :disabled="!dirty || saving || selectedParts.length < 3"
            @click="save"
          >
            <span>{{ saving ? 'Speichere …' : 'Kreisel speichern' }}</span>
            <span class="cta-arrow" aria-hidden="true">↓</span>
          </button>
        </div>
        <p class="cta-sub" :class="{ failed: saveFailed }" role="status">
          {{ saveMessage ?? (dirty ? 'Ungespeicherte Änderungen' : 'Alles gespeichert') }}
        </p>
      </div>
    </div>
  </main>
</template>

<style scoped>
.build {
  grid-column: 1;
}

.head-tools {
  display: flex;
  align-items: center;
  gap: 10px;
}

.explode {
  padding: 3px 8px;
  border: 1px solid var(--line-strong);
  border-radius: 2px;
  background: none;
  font-family: var(--font-mono);
  font-size: 0.68rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-muted);
  cursor: pointer;
}

.explode.active,
.explode:hover {
  border-color: var(--text);
  color: var(--text);
}

.swatch {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 7px;
  border-radius: 50%;
  vertical-align: 1px;
  box-shadow: 0 0 6px currentColor;
}

.values {
  grid-column: 3;
}

.collection {
  display: grid;
  gap: 10px;
  margin-bottom: 20px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--line);
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.chip {
  max-width: 150px;
  padding: 8px 12px;
  overflow: hidden;
  border: 1px solid var(--line-strong);
  border-radius: 3px;
  background: #0c0c0e;
  font-size: 0.85rem;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  transition:
    background-color 0.15s,
    border-color 0.15s,
    color 0.15s;
}

.chip:hover {
  border-color: var(--text-muted);
}

.chip.active {
  border-color: var(--text);
  background: var(--text);
  color: var(--bg);
}

.chip.new {
  border-style: dashed;
  color: var(--text-muted);
}

.chip.new.active {
  border-style: solid;
  color: var(--bg);
}

.slot + .slot {
  margin-top: 18px;
}

.slot-head {
  margin-bottom: 10px;
}

.options {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.option {
  position: relative;
  display: grid;
  align-content: start;
  gap: 4px;
  min-height: 74px;
  padding: 12px 10px;
  border: 1px solid var(--line);
  border-radius: 3px;
  background: #0c0c0e;
  text-align: left;
  cursor: pointer;
  transition:
    background-color 0.15s,
    border-color 0.15s;
}

.option:hover {
  border-color: var(--text-faint);
  background: var(--surface-raised);
}

.option.active {
  border-color: var(--text);
  background: var(--surface-raised);
}

.option.active::after {
  content: '';
  position: absolute;
  top: 8px;
  right: 8px;
  width: 5px;
  height: 5px;
  background: var(--text);
}

.option-name {
  font-size: 0.95rem;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.option-sub {
  font-family: var(--font-mono);
  font-size: 0.66rem;
  line-height: 1.5;
  color: var(--text-muted);
}

.option-weight {
  margin-top: auto;
  font-family: var(--font-mono);
  font-size: 0.66rem;
  color: var(--text-faint);
}

.name-field {
  display: grid;
  gap: 10px;
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px solid var(--line);
}

.name-field input {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid var(--line-strong);
  border-radius: 3px;
  background: #0c0c0e;
  color: var(--text);
  font: inherit;
  font-size: 1.05rem;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.name-field input:focus {
  outline: none;
  border-color: var(--text-muted);
}

.build-name {
  margin: 0 0 6px;
  font-size: 2.3rem;
  font-weight: 600;
  line-height: 1.05;
  letter-spacing: -0.035em;
  overflow-wrap: anywhere;
}

.build-parts {
  margin: 0 0 22px;
  font-family: var(--font-mono);
  font-size: 0.75rem;
  color: var(--text-muted);
}

.values .stats {
  margin-bottom: 22px;
}

.values .specs {
  margin: 0;
}

.spec-delta {
  margin-right: 8px;
  font-size: 0.75rem;
  color: var(--text-muted);
}

.actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.actions .btn-secondary {
  padding: 18px 22px;
  background: var(--bg);
}

.cta-sub.failed {
  color: var(--text);
  outline: 1px solid var(--line-strong);
}

.actions .btn-secondary.danger:not(:disabled):hover {
  border-color: var(--text);
  color: var(--text);
}

.actions .btn-secondary:disabled {
  color: var(--text-faint);
  cursor: default;
}

.actions .btn-secondary:disabled:hover {
  border-color: var(--line-strong);
}

@media (prefers-reduced-motion: reduce) {
  .option {
    transition: none;
  }
}
</style>
