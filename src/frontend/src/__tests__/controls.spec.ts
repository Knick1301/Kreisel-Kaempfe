import { describe, expect, it } from 'vitest'
import { rotateInput } from '@/game/physics/controls'

describe('rotateInput', () => {
  it('Startansicht: W = −z, D = +x', () => {
    const w = rotateInput(0, -1, 0)
    const d = rotateInput(1, 0, 0)
    expect(w.x).toBeCloseTo(0, 6)
    expect(w.z).toBeCloseTo(-1, 6)
    expect(d.x).toBeCloseTo(1, 6)
    expect(d.z).toBeCloseTo(0, 6)
  })

  it('Kamera von der Seite (+x): W zeigt nach −x, D nach −z', () => {
    // Kamera steht auf +x und blickt zur Mitte: yaw = atan2(1, 0) = π/2
    const w = rotateInput(0, -1, Math.PI / 2)
    const d = rotateInput(1, 0, Math.PI / 2)
    expect(w.x).toBeCloseTo(-1, 6)
    expect(w.z).toBeCloseTo(0, 6)
    expect(d.x).toBeCloseTo(0, 6)
    expect(d.z).toBeCloseTo(-1, 6)
  })

  it('Kamera gegenüber: alles gespiegelt', () => {
    const w = rotateInput(0, -1, Math.PI)
    const d = rotateInput(1, 0, Math.PI)
    expect(w.x).toBeCloseTo(0, 6)
    expect(w.z).toBeCloseTo(1, 6)
    expect(d.x).toBeCloseTo(-1, 6)
    expect(d.z).toBeCloseTo(0, 6)
  })
})
