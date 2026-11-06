/**
 * Arena-Definitionen ohne three.js-Abhängigkeit (Form, Taschen, Höhenprofil, optische Varianten).
 * Koordinaten: y nach oben, Polarwinkel θ in der Bodenebene mit x = r·cos θ, z = r·sin θ.
 * Form, Taschenwinkel und Varianten müssen zu `Arena.java` im Backend passen (Tests auf beiden Seiten).
 * Eine Arena bestimmt Form und Spielwerte, ihre Varianten nur das Aussehen.
 */

/** Bauweise der 3D-Arena (siehe `components/three/arenas`). */
export type ArenaStyle = 'classic' | 'octagon' | 'cyclone' | 'glacier'

export interface ArenaDefinition {
  id: string
  name: string
  style: ArenaStyle
  /** Radius der Bowl bis zum Rand (beim Oktagon: Abstand Mitte–Wand) */
  radius: number
  /** Krümmung der Bowl: y = curvature · r² */
  curvature: number
  /** Tornado-Ridge: ringförmige Erhebung, auf der die Kreisel am Anfang kreisen */
  ridge: { r: number; height: number; width: number }
  /** Kuppel in der Mitte (Zyklon), fehlt bei flacher Mitte */
  dome?: { r: number; height: number }
  /** Höhe des Rands über der Bowl-Kante */
  rimHeight: number
  /** Polarwinkel (rad) der Ring-Out-Taschen */
  pockets: readonly number[]
  /** halbe Öffnungsbreite einer Tasche (rad) */
  pocketHalfWidth: number
  /** wie weit eine Tasche nach außen reicht */
  pocketDepth: number
  /** wie tief der Taschenboden unter dem Rand liegt */
  pocketDrop: number
  /** Kurzbeschreibung des Spielgefühls für die Arena-Wahl */
  description: string
  /** Spielwerte wie in `Arena.java` (Faktoren, 1 = Standard), nur zur Anzeige */
  play: ArenaPlay
  /** Optische Varianten, die erste ist die Vorgabe */
  variants: readonly ArenaVariant[]
  /** Nicht mehr wählbar, nur noch für Replays */
  hidden?: boolean
}

export interface ArenaPlay {
  friction: number
  ringOut: number
  clash: number
  impact: number
}

export interface ArenaVariant {
  id: string
  name: string
  palette: ArenaPalette
}

/** Farben einer Variante. Jede Bauweise nutzt dieselben Slots. */
export interface ArenaPalette {
  /** Verlauf des Bodens von der Mitte zum Rand (5 Stopps) */
  floor: readonly [string, string, string, string, string]
  /** Hauptmetall (Wände, Rand) */
  metal: string
  /** Zierteile (Nieten, Kappen, Kanten), meist Gold oder Chrom */
  trim: string
  /** Leuchtende Kunststoff-Teile */
  accent: string
  /** Zweite Leuchtfarbe */
  accent2: string
  /** Markierungen im Boden */
  line: string
  pocketFloor: string
  /** Taschenboden leuchtet */
  pocketGlow: boolean
  /** Glanz des Bodens: 0 matt bis 1 spiegelnd */
  gloss: number
}

const deg = (d: number) => (d * Math.PI) / 180

const CLASSIC_PALETTE: ArenaPalette = {
  floor: ['#2a2a2f', '#1c1c20', '#111114', '#0b0b0d', '#060607'],
  metal: '#141418',
  trim: '#ededef',
  accent: '#ededef',
  accent2: '#8b8b94',
  line: '#34343c',
  pocketFloor: '#0a0a0c',
  pocketGlow: false,
  gloss: 0.5,
}

const VULKAN_PALETTE: ArenaPalette = {
  floor: ['#3a2a24', '#271a15', '#170f0c', '#0d0807', '#060404'],
  metal: '#1b1513',
  trim: '#d9a066',
  accent: '#ff7a3d',
  accent2: '#ffb347',
  line: '#5a2e1e',
  pocketFloor: '#ff5a1f',
  pocketGlow: true,
  gloss: 0.4,
}

export const STANDARD_ARENA: ArenaDefinition = {
  id: 'standard',
  name: 'Standard-Stadion',
  style: 'classic',
  radius: 4,
  curvature: 0.05,
  ridge: { r: 3.05, height: 0.07, width: 0.22 },
  rimHeight: 0.1,
  pockets: [Math.PI / 2, (7 * Math.PI) / 6, (11 * Math.PI) / 6],
  pocketHalfWidth: deg(14),
  pocketDepth: 0.95,
  pocketDrop: 0.45,
  play: { friction: 1, ringOut: 1, clash: 1, impact: 1 },
  description: 'Klassische Bowl mit Tornado-Ridge und drei Taschen.',
  variants: [
    { id: 'klassik', name: 'Klassik', palette: CLASSIC_PALETTE },
    { id: 'vulkan', name: 'Vulkan', palette: VULKAN_PALETTE },
  ],
}

/** Achteckiger Stahlkäfig: flacher Boden, hohe Wände, vier schmale Lücken. */
export const OKTAGON_ARENA: ArenaDefinition = {
  id: 'oktagon',
  name: 'Stahl-Oktagon',
  style: 'octagon',
  radius: 4.1,
  curvature: 0.012,
  ridge: { r: 3.0, height: 0, width: 0.3 },
  rimHeight: 0.06,
  pockets: [Math.PI / 4, (3 * Math.PI) / 4, (5 * Math.PI) / 4, (7 * Math.PI) / 4],
  pocketHalfWidth: deg(9),
  pocketDepth: 1.0,
  pocketDrop: 0.42,
  play: { friction: 0.98, ringOut: 0.75, clash: 1.15, impact: 0.95 },
  description:
    'Stahlkäfig mit flachem Boden: Die Wände werfen zurück, viele Clashes, kaum Ring-Outs.',
  variants: [
    {
      id: 'chrom',
      name: 'Chromwerk',
      palette: {
        floor: ['#5b5f68', '#474a52', '#34363d', '#25272c', '#17181c'],
        metal: '#c9ced6',
        trim: '#e3c47b',
        accent: '#6fe0bf',
        accent2: '#d6fff3',
        line: '#7c818c',
        pocketFloor: '#6fe0bf',
        pocketGlow: true,
        gloss: 0.75,
      },
    },
    {
      id: 'neon',
      name: 'Neon-Nacht',
      palette: {
        floor: ['#17131f', '#110e18', '#0c0a12', '#08070c', '#040306'],
        metal: '#2a2833',
        trim: '#b9c1cd',
        accent: '#b48cff',
        accent2: '#5cc8ff',
        line: '#6d4bc4',
        pocketFloor: '#b48cff',
        pocketGlow: true,
        gloss: 0.9,
      },
    },
  ],
}

/** Tiefer, steiler Kessel mit Kuppel in der Mitte und sechs kleinen Taschen. */
export const ZYKLON_ARENA: ArenaDefinition = {
  id: 'zyklon',
  name: 'Zyklon-Kessel',
  style: 'cyclone',
  radius: 3.9,
  curvature: 0.085,
  ridge: { r: 2.65, height: 0.05, width: 0.24 },
  dome: { r: 1.25, height: 0.3 },
  rimHeight: 0.16,
  pockets: [30, 90, 150, 210, 270, 330].map(deg),
  pocketHalfWidth: deg(8),
  pocketDepth: 0.85,
  pocketDrop: 0.5,
  play: { friction: 1, ringOut: 1.35, clash: 0.92, impact: 1.04 },
  description:
    'Steiler Kessel mit Kuppel: Die Kreisel jagen über die Wand, harte Treffer, sechs Taschen.',
  variants: [
    {
      id: 'sturm',
      name: 'Sturmauge',
      palette: {
        floor: ['#2b3a52', '#1f2b3f', '#16202f', '#0e1520', '#070b11'],
        metal: '#b4bac4',
        trim: '#dfe6f0',
        accent: '#5cb8ff',
        accent2: '#c7e8ff',
        line: '#3f6a9c',
        pocketFloor: '#5cb8ff',
        pocketGlow: true,
        gloss: 0.6,
      },
    },
    {
      id: 'sand',
      name: 'Sandsturm',
      palette: {
        floor: ['#a9875a', '#8a6b43', '#6a5031', '#4a3721', '#2a1f13'],
        metal: '#e3c47b',
        trim: '#fff1c9',
        accent: '#ff9f6b',
        accent2: '#ffd9a8',
        line: '#5e4426',
        pocketFloor: '#ff8a4c',
        pocketGlow: true,
        gloss: 0.35,
      },
    },
  ],
}

/** Weite, flache, spiegelglatte Schale mit Kristallrand und zwei breiten Spalten. */
export const GLETSCHER_ARENA: ArenaDefinition = {
  id: 'gletscher',
  name: 'Gletscher',
  style: 'glacier',
  radius: 4.6,
  curvature: 0.022,
  ridge: { r: 3.35, height: 0.035, width: 0.45 },
  rimHeight: 0.1,
  pockets: [Math.PI / 2, (3 * Math.PI) / 2],
  pocketHalfWidth: deg(18),
  pocketDepth: 1.0,
  pocketDrop: 0.45,
  play: { friction: 0.94, ringOut: 1.5, clash: 0.9, impact: 0.96 },
  description:
    'Weite Eisfläche, kaum Reibung: Getroffene rutschen weit, sonst entscheidet die Ausdauer.',
  variants: [
    {
      id: 'eis',
      name: 'Gletschereis',
      palette: {
        floor: ['#cfe6f5', '#a9cde6', '#7fa9c8', '#4f7a9c', '#22405a'],
        metal: '#dfe9f2',
        trim: '#ffffff',
        accent: '#8fd3ff',
        accent2: '#e6f7ff',
        line: '#ffffff',
        pocketFloor: '#7fd0ff',
        pocketGlow: true,
        gloss: 0.95,
      },
    },
    {
      id: 'aurora',
      name: 'Polarlicht',
      palette: {
        floor: ['#183a44', '#122c35', '#0d2027', '#08151a', '#030a0d'],
        metal: '#8fa3b0',
        trim: '#d6f5ff',
        accent: '#6ff0c0',
        accent2: '#c39bff',
        line: '#3fd6a8',
        pocketFloor: '#6ff0c0',
        pocketGlow: true,
        gloss: 0.95,
      },
    },
  ],
}

/**
 * Früherer Vulkan-Krater (kleiner, steiler, zwei breite Lava-Taschen). Nicht mehr wählbar, der Look ist jetzt
 * die Variante „Vulkan“ des Standard-Stadions. Bleibt für Replays alter Kämpfe.
 */
export const KRATER_ARENA: ArenaDefinition = {
  id: 'krater',
  name: 'Vulkan-Krater',
  style: 'classic',
  radius: 3.6,
  curvature: 0.075,
  ridge: { r: 2.55, height: 0.09, width: 0.2 },
  rimHeight: 0.14,
  pockets: [0, Math.PI],
  pocketHalfWidth: deg(22),
  pocketDepth: 0.9,
  pocketDrop: 0.5,
  play: { friction: 1.03, ringOut: 2, clash: 1, impact: 1 },
  description: 'Steil, rau, zwei breite Lava-Taschen.',
  variants: [{ id: 'vulkan', name: 'Vulkan', palette: VULKAN_PALETTE }],
  hidden: true,
}

export const ARENAS: Readonly<Record<string, ArenaDefinition>> = {
  [STANDARD_ARENA.id]: STANDARD_ARENA,
  [OKTAGON_ARENA.id]: OKTAGON_ARENA,
  [ZYKLON_ARENA.id]: ZYKLON_ARENA,
  [GLETSCHER_ARENA.id]: GLETSCHER_ARENA,
  [KRATER_ARENA.id]: KRATER_ARENA,
}

/** Wählbare Arenen in der Reihenfolge der Auswahl. */
export const ARENA_LIST: readonly ArenaDefinition[] = [
  STANDARD_ARENA,
  OKTAGON_ARENA,
  ZYKLON_ARENA,
  GLETSCHER_ARENA,
]

export function arenaById(id?: string | null): ArenaDefinition {
  return (id && ARENAS[id]) || STANDARD_ARENA
}

/** Variante einer Arena, unbekannte oder fehlende: die Vorgabe. */
export function arenaVariant(arena: ArenaDefinition, variantId?: string | null): ArenaVariant {
  return arena.variants.find((v) => v.id === variantId) ?? arena.variants[0]!
}

/** Größte Ausdehnung eines Kreisels in Welt-Einheiten. */
export const BEY_SIZE = 1.45
/** Mittelpunktabstand zweier Kreisel im Moment des Kontakts. */
export const CONTACT_DIST = 1.3
/** So weit dürfen Kreisel-Mittelpunkte vom Zentrum weg, ohne über den Rand zu ragen. */
export const playRadius = (a: ArenaDefinition) => a.radius - 0.72

/** Bowl ohne Ridge (Showcase-Szene auf Home/Baukasten). */
export function baseBowlY(a: ArenaDefinition, r: number) {
  return a.curvature * r * r
}

/** Kuppel in der Mitte: weicher Hügel, der bei `dome.r` flach ausläuft. */
function domeY(a: ArenaDefinition, r: number) {
  if (!a.dome || r >= a.dome.r) return 0
  const u = 1 - (r / a.dome.r) ** 2
  return a.dome.height * u * u
}

/** Höhe der Bowl-Oberfläche inklusive Tornado-Ridge und Kuppel. */
export function bowlY(a: ArenaDefinition, r: number) {
  const d = (r - a.ridge.r) / a.ridge.width
  return a.curvature * r * r + a.ridge.height * Math.exp(-d * d) + domeY(a, r)
}

/** Steigung dy/dr der Bowl (für Teile, die nach einem Burst zur Mitte rutschen). */
export function bowlSlope(a: ArenaDefinition, r: number) {
  const d = (r - a.ridge.r) / a.ridge.width
  let slope = 2 * a.curvature * r - ((2 * d) / a.ridge.width) * a.ridge.height * Math.exp(-d * d)
  if (a.dome && r < a.dome.r) {
    const u = r / a.dome.r
    slope += (-4 * a.dome.height * u * (1 - u * u)) / a.dome.r
  }
  return slope
}

export function rimY(a: ArenaDefinition) {
  return baseBowlY(a, a.radius) + a.rimHeight
}

export function pocketFloorY(a: ArenaDefinition) {
  return rimY(a) - a.pocketDrop
}

/** Winkel auf (-π, π]. */
export function wrapAngle(angle: number) {
  let a = angle % (2 * Math.PI)
  if (a > Math.PI) a -= 2 * Math.PI
  if (a <= -Math.PI) a += 2 * Math.PI
  return a
}

/** Index der Tasche, in deren Öffnung der Winkel liegt, sonst -1. */
export function pocketAt(a: ArenaDefinition, angle: number) {
  return a.pockets.findIndex((p) => Math.abs(wrapAngle(angle - p)) <= a.pocketHalfWidth + 1e-9)
}

/** Winkelbereiche des Rands zwischen den Taschen, für die Geometrie. */
export function rimSegments(a: ArenaDefinition): [number, number][] {
  const sorted = [...a.pockets].sort((x, y) => x - y)
  return sorted.map((p, i) => {
    const next = sorted[(i + 1) % sorted.length]! + (i + 1 === sorted.length ? 2 * Math.PI : 0)
    return [p + a.pocketHalfWidth, next - a.pocketHalfWidth]
  })
}

/** Eine Zeile der Spielwert-Anzeige: Stufe 1–5 (3 = Standard) und Abweichung in Prozent. */
export interface ArenaTrait {
  label: string
  level: number
  percent: number
}

/** Spielwerte einer Arena für die Auswahl, relativ zum Standard-Stadion. */
export function arenaTraits(a: ArenaDefinition): ArenaTrait[] {
  const trait = (label: string, factor: number, step: number): ArenaTrait => ({
    label,
    level: Math.max(1, Math.min(5, Math.round(3 + (factor - 1) / step))),
    percent: Math.round((factor - 1) * 100),
  })
  return [
    trait('Clashes', a.play.clash, 0.08),
    trait('Wucht', a.play.impact, 0.04),
    trait('Ring-Outs', a.play.ringOut, 0.2),
    trait('Reibung', a.play.friction, 0.03),
  ]
}
