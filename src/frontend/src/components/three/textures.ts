import { CanvasTexture, SRGBColorSpace } from 'three'

/** Weicher, runder Kontaktschatten. */
export function makeShadowTexture() {
  const c = document.createElement('canvas')
  c.width = c.height = 128
  const g = c.getContext('2d')!
  const grad = g.createRadialGradient(64, 64, 0, 64, 64, 64)
  grad.addColorStop(0, 'rgba(0,0,0,0.65)')
  grad.addColorStop(1, 'rgba(0,0,0,0)')
  g.fillStyle = grad
  g.fillRect(0, 0, 128, 128)
  return new CanvasTexture(c)
}

/** Verlauf der Bowl von der Mitte (hell) zum Rand (dunkel), entlang der Profil-UV. */
export function makeFloorTexture() {
  const c = document.createElement('canvas')
  c.width = 4
  c.height = 256
  const g = c.getContext('2d')!
  const grad = g.createLinearGradient(0, 0, 0, 256)
  grad.addColorStop(0, '#2a2a2f')
  grad.addColorStop(0.18, '#1c1c20')
  grad.addColorStop(0.45, '#111114')
  grad.addColorStop(0.88, '#0b0b0d')
  grad.addColorStop(1, '#060607')
  g.fillStyle = grad
  g.fillRect(0, 0, 4, 256)
  const tex = new CanvasTexture(c)
  tex.colorSpace = SRGBColorSpace
  tex.flipY = false
  return tex
}
