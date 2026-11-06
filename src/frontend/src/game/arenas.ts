/**
 * Arena-Definitionen ohne three.js-Abhängigkeit (Geometrie, Taschen, Höhenprofil).
 * Koordinaten: y nach oben, Polarwinkel θ in der Bodenebene mit x = r·cos θ, z = r·sin θ.
 * Die Taschenwinkel müssen zu `Arena.java` im Backend passen (Tests auf beiden Seiten).
 */
export interface ArenaDefinition {
  id: string
  name: string
  /** Radius der Bowl bis zum Rand */
  radius: number
  /** Krümmung der Bowl: y = curvature · r² */
  curvature: number
  /** Tornado-Ridge: ringförmige Erhebung, auf der die Kreisel am Anfang kreisen */
  ridge: { r: number; height: number; width: number }
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
  /** Kurzbeschreibung für die Arena-Wahl */
  description: string
  theme: ArenaTheme
}

export interface ArenaTheme {
  /** Verlauf der Bowl von der Mitte zum Rand (5 Stopps) */
  floor: readonly [string, string, string, string, string]
  metal: string
  /** Farbe der Akzentlinien an Rand und Taschen */
  accent: string
  pocketFloor: string
  /** Taschenboden leuchtet (Lava) */
  pocketGlow: boolean
  ridgeLine: string
}

export const STANDARD_ARENA: ArenaDefinition = {
  id: 'standard',
  name: 'Standard-Stadion',
  radius: 4,
  curvature: 0.05,
  ridge: { r: 3.05, height: 0.07, width: 0.22 },
  rimHeight: 0.1,
  pockets: [Math.PI / 2, (7 * Math.PI) / 6, (11 * Math.PI) / 6],
  pocketHalfWidth: (14 * Math.PI) / 180,
  pocketDepth: 0.95,
  pocketDrop: 0.45,
  description: 'Klassische Bowl mit Tornado-Ridge und drei Taschen.',
  theme: {
    floor: ['#2a2a2f', '#1c1c20', '#111114', '#0b0b0d', '#060607'],
    metal: '#141418',
    accent: '#ededef',
    pocketFloor: '#0a0a0c',
    pocketGlow: false,
    ridgeLine: '#34343c',
  },
}

/** Kleiner, steiler Krater mit rauem Boden und zwei breiten Lava-Taschen: viele Ring-Outs. */
export const KRATER_ARENA: ArenaDefinition = {
  id: 'krater',
  name: 'Vulkan-Krater',
  radius: 3.6,
  curvature: 0.075,
  ridge: { r: 2.55, height: 0.09, width: 0.2 },
  rimHeight: 0.14,
  pockets: [0, Math.PI],
  pocketHalfWidth: (22 * Math.PI) / 180,
  pocketDepth: 0.9,
  pocketDrop: 0.5,
  description: 'Steil, rau, zwei breite Lava-Taschen – Ring-Outs doppelt so wahrscheinlich.',
  theme: {
    floor: ['#3a2a24', '#271a15', '#170f0c', '#0d0807', '#060404'],
    metal: '#1b1513',
    accent: '#ff7a3d',
    pocketFloor: '#ff5a1f',
    pocketGlow: true,
    ridgeLine: '#5a2e1e',
  },
}

/** Weitere Arenen kommen hier dazu (und in `Arena.java`). */
export const ARENAS: Readonly<Record<string, ArenaDefinition>> = {
  [STANDARD_ARENA.id]: STANDARD_ARENA,
  [KRATER_ARENA.id]: KRATER_ARENA,
}

export const ARENA_LIST: readonly ArenaDefinition[] = [STANDARD_ARENA, KRATER_ARENA]

export function arenaById(id?: string | null): ArenaDefinition {
  return (id && ARENAS[id]) || STANDARD_ARENA
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

/** Höhe der Bowl-Oberfläche inklusive Tornado-Ridge. */
export function bowlY(a: ArenaDefinition, r: number) {
  const d = (r - a.ridge.r) / a.ridge.width
  return a.curvature * r * r + a.ridge.height * Math.exp(-d * d)
}

/** Steigung dy/dr der Bowl (für Teile, die nach einem Burst zur Mitte rutschen). */
export function bowlSlope(a: ArenaDefinition, r: number) {
  const d = (r - a.ridge.r) / a.ridge.width
  return 2 * a.curvature * r - ((2 * d) / a.ridge.width) * a.ridge.height * Math.exp(-d * d)
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
