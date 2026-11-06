/** Kleiner, schneller Seeded-PRNG (mulberry32). Liefert Zahlen in [0, 1). */
export function mulberry32(seed: number): () => number {
  let s = seed >>> 0
  return () => {
    s = (s + 0x6d2b79f5) >>> 0
    let t = s
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

/** Faltet einen 53-Bit-Seed aus dem Backend auf 32 Bit. */
export function foldSeed(seed: number): number {
  const lo = seed % 4294967296
  const hi = Math.floor(seed / 4294967296)
  return (lo ^ Math.imul(hi, 0x9e3779b1)) >>> 0
}

/** Eigener Zufallsstrom pro Zweck, damit z. B. Funken die Bahnen nicht verschieben. */
export function seededStream(seed: number, salt: number) {
  return mulberry32((foldSeed(seed) ^ Math.imul(salt + 1, 0x85ebca6b)) >>> 0)
}

export const range = (rng: () => number, min: number, max: number) => min + (max - min) * rng()
