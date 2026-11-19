import type { BeybladeStats, PartDto } from '@/api'
import { mulberry32 } from './prng'

/**
 * Aussehen der Teile als reine Daten (ohne three). Jedes Teil im Katalog hat über seinen Modellnamen
 * (`modelPath` ohne Ordner und Endung) ein eigenes Design. Die Geometrie dazu baut
 * `components/three/parts/partBuilders.ts`.
 */

export type WheelShape = 'scythe' | 'fortress' | 'vortex' | 'fang' | 'moon' | 'phantom' | 'feather'
export type TrackStyle = 'heavy' | 'orbit' | 'core' | 'tower' | 'bastion' | 'spring' | 'chalice'
export type TipStyle = 'flat' | 'needle' | 'ball' | 'claw' | 'bearing' | 'dome' | 'spear'

export interface WheelDesign {
  shape: WheelShape
  blades: number
  /** Kerben im Metallring */
  notches: number
  /** Dicke des Metallrings (1 = normal) */
  heft: number
  metal: string
  /** farbige Kunststoff-Ebene unter den Klingen */
  layer: string
  /** Leuchtakzent */
  accent: string
}

export interface TrackDesign {
  style: TrackStyle
  teeth: number
  metal: string
  accent: string
}

export interface TipDesign {
  style: TipStyle
  /** Farbe der Kontaktfläche (Gummi oder Metall) */
  color: string
  accent: string
}

export interface PartDesigns {
  wheel: WheelDesign
  track: TrackDesign
  tip: TipDesign
}

export const WHEEL_DESIGNS: Readonly<Record<string, WheelDesign>> = {
  'hells-scythe-blade': {
    shape: 'scythe',
    blades: 3,
    notches: 9,
    heft: 0.9,
    metal: '#d9dde4',
    layer: '#b3121f',
    accent: '#ff3b3b',
  },
  bollwerk: {
    shape: 'fortress',
    blades: 6,
    notches: 12,
    heft: 1.35,
    metal: '#a3abb8',
    layer: '#22365e',
    accent: '#6fa8ff',
  },
  wirbel: {
    shape: 'vortex',
    blades: 5,
    notches: 10,
    heft: 1.05,
    metal: '#cfd3da',
    layer: '#14594b',
    accent: '#38e0b4',
  },
  drachenzahn: {
    shape: 'fang',
    blades: 4,
    notches: 8,
    heft: 1,
    metal: '#e3c47b',
    layer: '#5e1310',
    accent: '#ffb347',
  },
  eisenmond: {
    shape: 'moon',
    blades: 2,
    notches: 16,
    heft: 1.25,
    metal: '#b9c1cd',
    layer: '#2c3050',
    accent: '#a9b8ff',
  },
  phantom: {
    shape: 'phantom',
    blades: 5,
    notches: 5,
    heft: 0.85,
    metal: '#8e909b',
    layer: '#3a1d5c',
    accent: '#c084fc',
  },
  greif: {
    shape: 'feather',
    blades: 4,
    notches: 12,
    heft: 1.1,
    metal: '#e6be55',
    layer: '#0f4fa8',
    accent: '#38b6ff',
  },
}

export const TRACK_DESIGNS: Readonly<Record<string, TrackDesign>> = {
  'hells-scythe-ratchet': { style: 'heavy', teeth: 9, metal: '#9ca2ac', accent: '#ff3b3b' },
  orbit: { style: 'orbit', teeth: 6, metal: '#b4bac4', accent: '#38bdf8' },
  kern: { style: 'core', teeth: 8, metal: '#a7adb7', accent: '#a3e635' },
  turm: { style: 'tower', teeth: 6, metal: '#c2c7cf', accent: '#fbbf24' },
  bastion: { style: 'bastion', teeth: 6, metal: '#8f96a1', accent: '#f87171' },
  feder: { style: 'spring', teeth: 5, metal: '#b9bfc8', accent: '#34d399' },
  kelch: { style: 'chalice', teeth: 4, metal: '#c9cdd4', accent: '#38b6ff' },
}

export const TIP_DESIGNS: Readonly<Record<string, TipDesign>> = {
  'hells-scythe-bit': { style: 'flat', color: '#26292f', accent: '#ff3b3b' },
  nadel: { style: 'needle', color: '#d4d8de', accent: '#38bdf8' },
  kugel: { style: 'ball', color: '#c9ced6', accent: '#a3e635' },
  kralle: { style: 'claw', color: '#e2561f', accent: '#ffb347' },
  lager: { style: 'bearing', color: '#d9dde3', accent: '#c084fc' },
  kuppel: { style: 'dome', color: '#3a3f48', accent: '#34d399' },
  speer: { style: 'spear', color: '#e6be55', accent: '#38b6ff' },
}

/** `fusion-wheels/drachenzahn.glb` → `drachenzahn` */
export function modelKey(modelPath: string | null | undefined) {
  if (!modelPath) return ''
  const file = modelPath.split('/').pop() ?? ''
  return file.replace(/\.[a-z0-9]+$/i, '')
}

/** Ersatz-Design aus den Werten, falls ein Teil (noch) kein eigenes Design hat. */
export function wheelByStats(attack: number, defense: number): WheelDesign {
  if (attack >= 6) return WHEEL_DESIGNS.drachenzahn!
  if (attack >= 4) return WHEEL_DESIGNS.phantom!
  if (defense >= 5) return WHEEL_DESIGNS.bollwerk!
  if (defense >= 4) return WHEEL_DESIGNS.eisenmond!
  return WHEEL_DESIGNS.wirbel!
}

function trackByStats(defense: number, stamina: number): TrackDesign {
  if (stamina >= 7) return defense >= 4 ? TRACK_DESIGNS.turm! : TRACK_DESIGNS.orbit!
  if (defense >= 6) return TRACK_DESIGNS.bastion!
  if (stamina >= 6) return TRACK_DESIGNS.feder!
  return TRACK_DESIGNS.kern!
}

function tipByStats(stamina: number, dash: number): TipDesign {
  if (dash >= 7) return stamina >= 4 ? TIP_DESIGNS.kralle! : TIP_DESIGNS['hells-scythe-bit']!
  if (stamina >= 7) return TIP_DESIGNS.lager!
  if (stamina >= 6) return TIP_DESIGNS.kuppel!
  if (dash <= 2) return TIP_DESIGNS.nadel!
  return TIP_DESIGNS.kugel!
}

/** Designs für Fusion Wheel, Spin Track und Tip eines gebauten Kreisels. */
export function designsFor(parts: readonly PartDto[]): PartDesigns {
  const [w, t, p] = parts
  return {
    wheel: WHEEL_DESIGNS[modelKey(w?.modelPath)] ?? wheelByStats(w?.attack ?? 3, w?.defense ?? 3),
    track: TRACK_DESIGNS[modelKey(t?.modelPath)] ?? trackByStats(t?.defense ?? 4, t?.stamina ?? 5),
    tip: TIP_DESIGNS[modelKey(p?.modelPath)] ?? tipByStats(p?.stamina ?? 5, p?.dash ?? 5),
  }
}

/**
 * Werte der Katalogteile wie in `parts/*.json` im Backend (ein Test prüft das). Damit sucht sich ein
 * KI-Gegner echte Teile aus, die zu seinen Werten passen.
 */
export const PART_STATS = {
  wheels: {
    'hells-scythe-blade': { attack: 7, defense: 1 },
    bollwerk: { attack: 1, defense: 5 },
    wirbel: { attack: 3, defense: 3 },
    drachenzahn: { attack: 6, defense: 2 },
    eisenmond: { attack: 2, defense: 4 },
    phantom: { attack: 5, defense: 2 },
    greif: { attack: 6, defense: 3 },
  },
  tracks: {
    'hells-scythe-ratchet': { defense: 7, stamina: 3 },
    orbit: { defense: 2, stamina: 8 },
    kern: { defense: 5, stamina: 5 },
    turm: { defense: 4, stamina: 7 },
    bastion: { defense: 6, stamina: 4 },
    feder: { defense: 3, stamina: 6 },
    kelch: { defense: 4, stamina: 6 },
  },
  tips: {
    'hells-scythe-bit': { stamina: 3, dash: 8 },
    nadel: { stamina: 8, dash: 2 },
    kugel: { stamina: 5, dash: 5 },
    kralle: { stamina: 4, dash: 7 },
    lager: { stamina: 7, dash: 3 },
    kuppel: { stamina: 6, dash: 4 },
    speer: { stamina: 5, dash: 6 },
  },
} as const

type PartTable = Readonly<Record<string, Readonly<Record<string, number>>>>

/** Modellnamen der drei Teile eines KI-Gegners. */
export interface BotLook {
  wheel: string
  track: string
  tip: string
}

/**
 * Eines der drei am besten passenden Teile, per Seed gezogen. Teile aus `avoid` (die des Gegners) kommen
 * nicht in Frage, solange es andere gibt: So sieht die KI nie aus wie der eigene Kreisel.
 */
function pickFit(
  table: PartTable,
  cost: (s: Readonly<Record<string, number>>) => number,
  avoid: readonly string[],
  rng: () => number,
) {
  const ranked = Object.keys(table).sort(
    (a, b) => cost(table[a]!) - cost(table[b]!) || a.localeCompare(b),
  )
  const pool = ranked.filter((k) => !avoid.includes(k))
  const top = (pool.length > 0 ? pool : ranked).slice(0, 3)
  const r = rng()
  return (r < 0.55 ? top[0] : r < 0.85 ? (top[1] ?? top[0]) : (top[2] ?? top[0]))!
}

/**
 * KI-Gegner haben keine Teile, nur Werte. Ihr Aussehen setzt sich aus echten Teilen zusammen, die zu den
 * Werten passen: Angriff kommt nur vom Fusion Wheel, Dash nur vom Tip, Verteidigung und Ausdauer teilen sich
 * die Teile. Über den Seed stabil (Replay sieht gleich aus), ohne die Teile des Gegners.
 */
export function botLook(
  stats: BeybladeStats,
  seed: number,
  avoid: readonly string[] = [],
): BotLook {
  const rng = mulberry32(seed >>> 0)
  const W: PartTable = PART_STATS.wheels
  const T: PartTable = PART_STATS.tracks
  const P: PartTable = PART_STATS.tips
  const wheel = pickFit(
    W,
    (w) => Math.abs(w.attack! - stats.attack) + 0.5 * Math.abs(w.defense! - stats.defense * 0.4),
    avoid,
    rng,
  )
  const restDefense = stats.defense - W[wheel]!.defense!
  const track = pickFit(
    T,
    (t) => Math.abs(t.defense! - restDefense) + 0.6 * Math.abs(t.stamina! - stats.stamina * 0.55),
    avoid,
    rng,
  )
  const restStamina = stats.stamina - T[track]!.stamina!
  const tip = pickFit(
    P,
    (p) => Math.abs(p.dash! - stats.dash) + 0.5 * Math.abs(p.stamina! - restStamina),
    avoid,
    rng,
  )
  return { wheel, track, tip }
}

/** Modellpfade für einen KI-Gegner (echte Teile, siehe `botLook`). */
export function botModelPaths(
  stats: BeybladeStats,
  seed: number,
  avoid: readonly string[] = [],
): string[] {
  const l = botLook(stats, seed, avoid)
  return [`fusion-wheels/${l.wheel}.glb`, `spin-tracks/${l.track}.glb`, `tips/${l.tip}.glb`]
}

/** Prozedurale Designs für einen KI-Gegner (Fallback ohne GLB), Akzente in seiner Farbe. */
export function botDesigns(
  stats: BeybladeStats,
  accent: string | undefined,
  seed: number,
  avoid: readonly string[] = [],
): PartDesigns {
  const l = botLook(stats, seed, avoid)
  const wheel = WHEEL_DESIGNS[l.wheel]!
  const color = accent ?? wheel.accent
  return {
    wheel: { ...wheel, accent: color, layer: shade(color, 0.35) },
    track: { ...TRACK_DESIGNS[l.track]!, accent: color },
    tip: { ...TIP_DESIGNS[l.tip]!, accent: color },
  }
}

/** Farbe abdunkeln (0 = schwarz, 1 = unverändert). */
export function shade(hex: string, factor: number) {
  const n = parseInt(hex.replace('#', ''), 16)
  const r = Math.round(((n >> 16) & 255) * factor)
  const g = Math.round(((n >> 8) & 255) * factor)
  const b = Math.round((n & 255) * factor)
  return `#${((1 << 24) | (r << 16) | (g << 8) | b).toString(16).slice(1)}`
}

/** Schlüssel, an dem man erkennt, ob sich ein Teil-Design geändert hat. */
export const designKey = (d: object) => JSON.stringify(d)
