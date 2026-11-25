/**
 * Pixeldichte fürs 3D-Bild, gedeckelt nach Gesamtpixeln. Auf großen Monitoren (1440p, 4K, Skalierung 150 %)
 * würde sonst ein Vielfaches der Pixel eines Laptops gerendert, das kostet mehr als jede Szene.
 */
export function cappedDpr(maxPixels = 2_200_000): [number, number] {
  const w = globalThis.innerWidth || 1280
  const h = globalThis.innerHeight || 720
  const device = globalThis.devicePixelRatio || 1
  const byPixels = Math.sqrt(maxPixels / (w * h))
  const max = Math.max(0.75, Math.min(1.5, device, byPixels))
  return [Math.min(1, max), max]
}
