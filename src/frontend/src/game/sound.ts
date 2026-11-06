import { ref } from 'vue'
import type { Side, TimelineEvent, UltType } from '@/api'
import { plateHit, spinLoop, type PlateOptions } from './metalSynth'

/**
 * Soundeffekte aus echten Aufnahmen in `src/assets/sounds/`.
 *
 * Jeder Effekt hat einen festen Namen (siehe {@link SLOTS} und `src/assets/sounds/README.md`). Dateien heißen
 * `<name>.ogg` (auch .mp3/.wav), Varianten `<name>-2.ogg`, `<name>-3.ogg` …; beim Abspielen wird zufällig
 * eine Variante gewählt und leicht in Tonhöhe und Lautstärke variiert. Vite listet die vorhandenen Dateien beim
 * Bauen, fehlende Effekte verursachen keine Anfragen.
 *
 * Fehlt eine Datei, rechnet das Spiel Metallklänge selbst (Modalsynthese, siehe `metalSynth.ts`): Treffer wie
 * ein Schlag auf ein Blech, mechanisches Surren. Für den Rest gibt es einen dezenten Ersatz aus Rauschen.
 *
 * Signalweg: Stimmen → (trocken + Hall-Anteil) → Master → Kompressor → Limiter → Ausgang.
 */

/** Alle Effekt-Namen. */
export const SLOTS = [
  'hit-light',
  'hit-heavy',
  'deflect',
  'smash-hit',
  'ult-cutin',
  'ult-ready',
  'ult-smash',
  'ult-shield',
  'ult-rush',
  'ult-surge',
  'burst',
  'ring-out',
  'spin-out',
  'launch',
  'countdown',
  'countdown-go',
  'spin-loop',
] as const
export type Slot = (typeof SLOTS)[number]

const files = import.meta.glob('../assets/sounds/*.{ogg,mp3,wav,m4a}', {
  eager: true,
  query: '?url',
  import: 'default',
}) as Record<string, string>

/** Vorhandene Dateien je Effekt. */
const SOURCES: Partial<Record<Slot, string[]>> = {}
for (const [path, url] of Object.entries(files)) {
  const name = path
    .split('/')
    .pop()!
    .replace(/\.[a-z0-9]+$/i, '')
  const slot = name.replace(/-\d+$/, '') as Slot
  if (!(SLOTS as readonly string[]).includes(slot)) continue
  ;(SOURCES[slot] ??= []).push(url)
}

/** Ist für diesen Effekt eine echte Aufnahme da? */
export function hasSample(slot: Slot) {
  return (SOURCES[slot]?.length ?? 0) > 0
}

const STORAGE_KEY = 'kk-sound-muted'
const VOLUME = 0.8

export const soundMuted = ref(readMuted())

function readMuted() {
  try {
    return globalThis.localStorage?.getItem(STORAGE_KEY) === '1'
  } catch {
    return false
  }
}

export function setSoundMuted(muted: boolean) {
  soundMuted.value = muted
  try {
    globalThis.localStorage?.setItem(STORAGE_KEY, muted ? '1' : '0')
  } catch {
    // privater Modus: dann eben nur für diese Sitzung
  }
  if (master && ctx) master.gain.setTargetAtTime(muted ? 0 : VOLUME, ctx.currentTime, 0.05)
}

// ---------------------------------------------------------------- Audio-Graph

let ctx: AudioContext | null = null
let master: GainNode | null = null
let reverb: ConvolverNode | null = null
let noiseBuffer: AudioBuffer | null = null
const buffers = new Map<string, AudioBuffer | null>()
const loading = new Map<string, Promise<AudioBuffer | null>>()

function audio(): AudioContext | null {
  if (ctx) {
    if (ctx.state === 'suspended') void ctx.resume().catch(() => undefined)
    return soundMuted.value ? null : ctx
  }
  if (soundMuted.value) return null
  try {
    const Ctx =
      globalThis.AudioContext ??
      (globalThis as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
    if (!Ctx) return null
    ctx = new Ctx()
    const c = ctx
    const comp = c.createDynamicsCompressor()
    comp.threshold.value = -16
    comp.knee.value = 8
    comp.ratio.value = 4
    comp.attack.value = 0.003
    comp.release.value = 0.2
    const limiter = c.createDynamicsCompressor()
    limiter.threshold.value = -1.5
    limiter.ratio.value = 20
    limiter.attack.value = 0.001
    master = c.createGain()
    master.gain.value = VOLUME
    master.connect(comp).connect(limiter).connect(c.destination)

    // Arena-Hall: erzeugte Impulsantwort
    reverb = c.createConvolver()
    reverb.buffer = impulse(c, 2.2, 2.6)
    const wet = c.createGain()
    wet.gain.value = 0.4
    reverb.connect(wet).connect(master)

    const len = c.sampleRate * 2
    noiseBuffer = c.createBuffer(1, len, c.sampleRate)
    const data = noiseBuffer.getChannelData(0)
    for (let i = 0; i < len; i++) data[i] = Math.random() * 2 - 1

    preload()
    return soundMuted.value ? null : c
  } catch {
    ctx = null
    return null
  }
}

function impulse(c: AudioContext, seconds: number, decay: number) {
  const len = Math.floor(c.sampleRate * seconds)
  const buf = c.createBuffer(2, len, c.sampleRate)
  for (let ch = 0; ch < 2; ch++) {
    const d = buf.getChannelData(ch)
    for (let i = 0; i < len; i++) d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / len, decay)
  }
  return buf
}

function load(url: string): Promise<AudioBuffer | null> {
  const c = ctx
  if (!c) return Promise.resolve(null)
  let p = loading.get(url)
  if (!p) {
    p = fetch(url)
      .then((r) => (r.ok ? r.arrayBuffer() : Promise.reject(new Error(String(r.status)))))
      .then((data) => c.decodeAudioData(data))
      .catch(() => null)
      .then((b) => {
        buffers.set(url, b)
        return b
      })
    loading.set(url, p)
  }
  return p
}

/** Alle vorhandenen Aufnahmen vorab laden, damit der erste Treffer nicht verzögert. */
function preload() {
  for (const urls of Object.values(SOURCES)) for (const u of urls ?? []) void load(u)
  prepareSounds()
}

// ---------------------------------------------------------------- Selbst gerechnete Metallklänge

/** Berechnete Klänge je Effekt, wenn es keine Aufnahme gibt */
const SYNTH: Partial<Record<Slot, AudioBuffer[]>> = {}

const PLATES: [Slot, PlateOptions][] = [
  // Stock auf Aluwand: dichtes Blech, harter Knack, gesättigt
  ...[1, 2, 3, 4].map((seed): [Slot, PlateOptions] => [
    'hit-heavy',
    { low: 85, high: 7000, modes: 60, decay: 0.9, crack: 1, drive: 0.75, length: 1.5, seed },
  ]),
  ...[11, 12, 13, 14].map((seed): [Slot, PlateOptions] => [
    'hit-light',
    { low: 260, high: 9500, modes: 44, decay: 0.38, crack: 0.9, drive: 0.55, length: 0.8, seed },
  ]),
  // Abpraller: weniger, klarere Moden, klingt länger nach
  ...[21, 22].map((seed): [Slot, PlateOptions] => [
    'deflect',
    { low: 650, high: 8000, modes: 14, decay: 1.1, crack: 0.6, drive: 0.25, length: 1.3, seed },
  ]),
  // Vernichtungsschlag: riesige, tiefe Platte, voll gesättigt
  [
    'smash-hit',
    { low: 45, high: 6000, modes: 80, decay: 2, crack: 1, drive: 1, length: 2.6, seed: 31 },
  ],
  [
    'burst',
    { low: 70, high: 8000, modes: 70, decay: 1.2, crack: 1, drive: 0.9, length: 2, seed: 41 },
  ],
]

let synthStarted = false

/**
 * Klänge nacheinander im Hintergrund rechnen (je einer pro Durchlauf), damit nichts ruckelt. Dauert
 * insgesamt Bruchteile einer Sekunde, meist ist alles fertig, bevor der Kampf beginnt.
 */
/** Rate der berechneten Klänge; der Browser rechnet beim Abspielen auf seine eigene um. */
const SYNTH_RATE = 48000
/** Berechnete Rohdaten je Effekt (ohne AudioContext, schon vor dem ersten Ton) */
const SYNTH_DATA: Partial<Record<Slot, Float32Array<ArrayBuffer>[]>> = {}

/**
 * Metallklänge im Leerlauf des Browsers vorrechnen, einen pro Durchlauf, damit nichts ruckelt. Am besten
 * schon beim Öffnen der Kampfseite aufrufen; braucht keinen Ton und keine Nutzeraktion.
 */
export function prepareSounds() {
  if (synthStarted) return
  synthStarted = true
  const jobs: (() => void)[] = []
  if (!hasSample('spin-loop')) {
    jobs.push(() => {
      SYNTH_DATA['spin-loop'] = [spinLoop(SYNTH_RATE)]
    })
  }
  for (const [slot, o] of PLATES) {
    if (hasSample(slot)) continue
    jobs.push(() => {
      ;(SYNTH_DATA[slot] ??= []).push(plateHit(SYNTH_RATE, o))
    })
  }
  const idle: (cb: () => void) => void =
    typeof globalThis.requestIdleCallback === 'function'
      ? (cb) => globalThis.requestIdleCallback(cb, { timeout: 500 })
      : (cb) => setTimeout(cb, 16)
  const step = () => {
    const job = jobs.shift()
    if (!job) return
    job()
    idle(step)
  }
  idle(step)
}

/** Berechnete Klänge als AudioBuffer (beim ersten Gebrauch umgewandelt). */
function synthBuffers(slot: Slot): AudioBuffer[] {
  const data = SYNTH_DATA[slot]
  if (!ctx || !data?.length) return []
  const have = SYNTH[slot] ?? []
  for (let i = have.length; i < data.length; i++) {
    const buf = ctx.createBuffer(1, data[i]!.length, SYNTH_RATE)
    buf.copyToChannel(data[i]!, 0)
    have.push(buf)
  }
  SYNTH[slot] = have
  return have
}

interface Opts {
  gain?: number
  /** Stereo −1 bis 1 */
  pan?: number
  /** Hall-Anteil 0–1 */
  send?: number
  /** Tonhöhe (1 = original) */
  rate?: number
  /** Zufällige Tonhöhen-Streuung ± */
  vary?: number
  delay?: number
}

function out(c: AudioContext, gain: number, o: Opts) {
  const g = c.createGain()
  g.gain.value = gain
  let node: AudioNode = g
  if (o.pan && c.createStereoPanner) {
    const p = c.createStereoPanner()
    p.pan.value = Math.max(-1, Math.min(1, o.pan))
    g.connect(p)
    node = p
  }
  node.connect(master!)
  if (o.send && reverb) {
    const s = c.createGain()
    s.gain.value = o.send
    node.connect(s).connect(reverb)
  }
  return g
}

/** Aufnahme abspielen; false, wenn es keine gibt (dann greift der Ersatz). */
function sample(slot: Slot, o: Opts = {}): boolean {
  const urls = SOURCES[slot]
  const c = audio()
  const synth = urls?.length ? [] : synthBuffers(slot)
  if (!urls?.length && !synth.length) return false
  if (!c) return true
  const start = (b: AudioBuffer | null) => {
    if (!b || !ctx) return
    const src = ctx.createBufferSource()
    src.buffer = b
    const vary = o.vary ?? 0.06
    src.playbackRate.value = (o.rate ?? 1) * (1 + (Math.random() * 2 - 1) * vary)
    src.connect(out(ctx, (o.gain ?? 1) * (0.9 + Math.random() * 0.2), o))
    src.start(ctx.currentTime + (o.delay ?? 0))
  }
  if (!urls?.length) {
    start(synth[Math.floor(Math.random() * synth.length)]!)
    return true
  }
  const url = urls[Math.floor(Math.random() * urls.length)]!
  const buf = buffers.get(url)
  if (buf !== undefined) start(buf)
  else void load(url).then(start)
  return true
}

// ---------------------------------------------------------------- Ersatz ohne Aufnahme (nur Geräusch)

function noise(
  filter: BiquadFilterType,
  from: number,
  to: number,
  peak: number,
  attack: number,
  decay: number,
  o: Opts = {},
) {
  const c = audio()
  if (!c || !noiseBuffer) return
  const at = c.currentTime + (o.delay ?? 0)
  const g = out(c, 1, o)
  const env = c.createGain()
  env.gain.setValueAtTime(0.0001, at)
  env.gain.exponentialRampToValueAtTime(peak, at + attack)
  env.gain.exponentialRampToValueAtTime(0.0001, at + attack + decay)
  env.connect(g)
  const src = c.createBufferSource()
  src.buffer = noiseBuffer
  const f = c.createBiquadFilter()
  f.type = filter
  f.frequency.setValueAtTime(from, at)
  f.frequency.exponentialRampToValueAtTime(Math.max(20, to), at + attack + decay)
  src.connect(f).connect(env)
  src.start(at, Math.random() * 1.5)
  src.stop(at + attack + decay + 0.05)
}

/** Tiefer Druck: tiefpassgefiltertes Rauschen mit fallender Grenzfrequenz. */
function thump(peak: number, decay: number, o: Opts = {}) {
  noise('lowpass', 420, 40, peak, 0.004, decay, { send: 0.3, ...o })
}

/** Einschlag: Knack plus Druck. */
function impact(i: number, o: Opts = {}) {
  noise('bandpass', 3800 - 1500 * i, 1200, 0.3 + 0.5 * i, 0.001, 0.06 + 0.1 * i, o)
  thump(0.4 + 0.6 * i, 0.15 + 0.35 * i, o)
}

// ---------------------------------------------------------------- Effekte

export function playClash(intensity: number, pan = 0) {
  const i = Math.max(0.1, Math.min(1, intensity))
  const heavy = i >= 0.45
  const ok = sample(heavy ? 'hit-heavy' : 'hit-light', {
    gain: 0.5 + 0.5 * i,
    pan,
    send: 0.3,
    // härter = tiefer
    rate: 1.12 - 0.25 * i,
  })
  // Schwere Treffer bekommen Druck darunter, dass es kracht
  if (!ok) impact(i, { pan })
  else if (heavy) thump(0.35 + 0.45 * i, 0.25 + 0.25 * i, { pan })
}

export function playDeflect(pan = 0) {
  if (!sample('deflect', { pan, send: 0.35, gain: 0.8 })) {
    noise('highpass', 5000, 2500, 0.25, 0.001, 0.08, { pan, send: 0.4 })
  }
}

export function playUltReady() {
  sample('ult-ready', { gain: 0.7, send: 0.3, vary: 0 })
}

/** Anime-Einblendung: Einschlag plus Klang der jeweiligen Ult. */
export function playUltCutIn(ult: UltType, mine: boolean) {
  const pan = mine ? -0.3 : 0.3
  if (!sample('ult-cutin', { gain: mine ? 1 : 0.8, pan, send: 0.4, vary: 0.02 })) {
    noise('highpass', 8000, 2000, 0.35, 0.001, 0.25, { send: 0.6, pan })
    thump(0.9, 1.1, { pan })
    noise('bandpass', 500, 4000, 0.25, 0.2, 0.3, { send: 0.4, pan })
  }
  playUltTheme(ult, mine ? 0.25 : 0.2, mine ? 1 : 0.8)
}

const ULT_SLOT: Record<UltType, Slot> = {
  SMASH: 'ult-smash',
  SHIELD: 'ult-shield',
  RUSH: 'ult-rush',
  SURGE: 'ult-surge',
}

function playUltTheme(ult: UltType, delay: number, gain: number) {
  // Ohne eigene Aufnahme (ult-smash.ogg …) bleibt es beim Sound der Einblendung, kein Rauschen
  sample(ULT_SLOT[ult], { delay, gain, send: 0.4, vary: 0.03 })
}

/** Ult im Replay ohne Einblendung. */
export function playUlt(ult: UltType) {
  playUltTheme(ult, 0, 0.8)
}

export function playSmashHit(pan = 0) {
  if (!sample('smash-hit', { pan, send: 0.5, vary: 0.03 })) {
    noise('lowpass', 5000, 100, 0.9, 0.002, 1.3, { pan, send: 0.7 })
    thump(1, 1.6, { pan })
  }
}

export function playBurst(pan = 0) {
  if (!sample('burst', { pan, send: 0.5 })) {
    noise('lowpass', 6000, 120, 0.9, 0.002, 1.1, { pan, send: 0.7 })
    thump(1, 1.2, { pan })
  }
}

export function playRingOut(pan = 0) {
  if (!sample('ring-out', { pan, send: 0.4 })) {
    noise('bandpass', 2500, 300, 0.4, 0.01, 0.5, { pan, send: 0.4 })
    impact(0.8, { pan, delay: 0.3 })
  }
}

export function playSpinOut() {
  if (!sample('spin-out', { send: 0.3 })) {
    for (let k = 0; k < 4; k++) impact(0.25 / (k + 1), { delay: 0.2 + k * (0.2 - k * 0.03) })
  }
}

export function playLaunch() {
  if (!sample('launch', { send: 0.3 })) {
    noise('bandpass', 600, 5000, 0.4, 0.08, 0.4, { send: 0.4 })
    impact(0.5, { delay: 0.35 })
  }
}

export function playCountdown(final: boolean) {
  if (sample(final ? 'countdown-go' : 'countdown', { send: 0.3, vary: 0 })) return
  if (final) {
    thump(0.9, 0.9)
    noise('highpass', 6000, 2000, 0.25, 0.001, 0.35, { send: 0.6 })
  } else {
    impact(0.3)
  }
}

export function playTimeout() {
  sample('countdown-go', { send: 0.3, vary: 0 })
}

/** Ton zu einem Timeline-Ereignis. `deflected`: Der Clash wurde abgewehrt. */
export function playEvent(e: TimelineEvent, deflected: boolean) {
  switch (e.type) {
    case 'LAUNCH':
      return playLaunch()
    case 'CLASH': {
      const pan = Math.cos(e.angle) * 0.6
      if (e.ult === 'SMASH') return playSmashHit(pan)
      if (deflected) return playDeflect(pan)
      return playClash(e.intensity, pan)
    }
    case 'ULT':
      return playUlt(e.ult)
    case 'BURST':
      return playBurst()
    case 'RING_OUT':
      return playRingOut(Math.cos(e.angle) * 0.7)
    case 'SPIN_OUT':
      return playSpinOut()
    case 'TIMEOUT':
      return playTimeout()
  }
}

// ---------------------------------------------------------------- Durchgehendes Surren

interface HumVoice {
  /** Aufnahme in Schleife, oder Ersatz: gefiltertes Rauschen */
  source: AudioBufferSourceNode
  filter: BiquadFilterNode
  gain: GainNode
  pan: StereoPannerNode | null
  /** Tonhöhe der Aufnahme bei vollem Spin */
  sampled: boolean
}

/**
 * Surren der beiden Kreisel. Mit `spin-loop` als Aufnahme: Tonhöhe folgt dem Spin, Lautstärke und Helligkeit
 * Spin und Tempo. Ohne Aufnahme: ein leises, luftiges Sirren aus gefiltertem Rauschen.
 */
class SpinHum {
  private voices: Partial<Record<Side, HumVoice>> = {}
  private bus: GainNode | null = null

  async start() {
    const c = audio()
    if (!c || this.bus) return
    const bus = c.createGain()
    this.bus = bus
    bus.gain.value = 0
    bus.gain.setTargetAtTime(1, c.currentTime, 0.3)
    bus.connect(master!)
    if (reverb) {
      const s = c.createGain()
      s.gain.value = 0.12
      bus.connect(s).connect(reverb)
    }
    const url = SOURCES['spin-loop']?.[0]
    const buf = url ? await load(url) : (synthBuffers('spin-loop')[0] ?? null)
    if (this.bus !== bus) return
    for (const side of ['A', 'B'] as const)
      this.voices[side] = this.voice(c, buf, side === 'A' ? 0 : 0.37)
  }

  private voice(c: AudioContext, buf: AudioBuffer | null, offset: number): HumVoice {
    const gain = c.createGain()
    gain.gain.value = 0
    let node: AudioNode = gain
    let pan: StereoPannerNode | null = null
    if (c.createStereoPanner) {
      pan = c.createStereoPanner()
      gain.connect(pan)
      node = pan
    }
    node.connect(this.bus!)
    const filter = c.createBiquadFilter()
    filter.connect(gain)
    const source = c.createBufferSource()
    source.loop = true
    if (buf) {
      source.buffer = buf
      filter.type = 'lowpass'
      filter.frequency.value = 8000
    } else {
      source.buffer = noiseBuffer
      filter.type = 'bandpass'
      filter.Q.value = 9
      filter.frequency.value = 1500
    }
    source.connect(filter)
    source.start(0, buf ? (offset * buf.duration) % buf.duration : Math.random())
    return { source, filter, gain, pan, sampled: !!buf }
  }

  /**
   * @param energy Spin 0–1
   * @param speed  Bewegung 0–1
   * @param pan    −1 links bis 1 rechts
   * @param ducked leiser, z. B. während einer Einblendung
   */
  update(side: Side, energy: number, speed: number, pan: number, ducked: boolean) {
    const v = this.voices[side]
    if (!v || !ctx) return
    const t = ctx.currentTime
    const e = Math.max(0, Math.min(1, energy))
    if (v.sampled) {
      v.source.playbackRate.setTargetAtTime(0.55 + 0.6 * e + 0.1 * speed, t, 0.08)
      v.filter.frequency.setTargetAtTime(1200 + 7000 * e, t, 0.08)
    } else {
      v.filter.frequency.setTargetAtTime(700 + 2600 * e + 1500 * speed, t, 0.06)
    }
    const base = v.sampled ? 0.25 + 0.45 * e : 0.04 + 0.08 * e
    const level = e <= 0.002 ? 0 : (base + 0.1 * speed) * (ducked ? 0.3 : 1)
    v.gain.gain.setTargetAtTime(level, t, 0.06)
    v.pan?.pan.setTargetAtTime(Math.max(-1, Math.min(1, pan)), t, 0.05)
  }

  stop() {
    const c = ctx
    const bus = this.bus
    if (!c || !bus) return
    bus.gain.setTargetAtTime(0, c.currentTime, 0.15)
    const voices = Object.values(this.voices)
    this.voices = {}
    this.bus = null
    setTimeout(() => {
      for (const v of voices) {
        try {
          v.source.stop()
        } catch {
          // schon gestoppt
        }
      }
      bus.disconnect()
    }, 800)
  }

  get running() {
    return !!this.bus
  }
}

export const spinHum = new SpinHum()
