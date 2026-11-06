import { describe, expect, it } from 'vitest'
import type { UltType } from '@/api'
import { createPose } from '@/game/choreographer'
import type { UltWindow } from '@/game/playback'
import { UltFx } from '@/components/three/battle/ultFx'

const poses = () => {
  const A = createPose()
  const B = createPose()
  B.x = 2
  return { A, B }
}

function run(ult: UltType) {
  const fx = new UltFx(false)
  fx.setViewport(1280, 720)
  const windows: UltWindow[] = [{ side: 'A', ult, start: 1000, end: 4000, power: 1.1 }]
  const p = poses()
  fx.burst(p.A, ult, 1.1, '#ff0000')
  for (let t = 1000; t < 2000; t += 16) fx.update(t, 0.016, p, windows, false)
  return fx
}

describe('UltFx', () => {
  it('Ansturm: Blitze sichtbar, Kuppel nicht', () => {
    const fx = run('RUSH')
    const [, lightningA, shieldA] = fx.object.children
    expect(lightningA!.visible).toBe(true)
    expect(shieldA!.visible).toBe(false)
    fx.dispose()
  })

  it('Schildwall: Kuppel sichtbar, Treffer lässt sie aufleuchten', () => {
    const fx = run('SHIELD')
    const shieldA = fx.object.children[2]!
    expect(shieldA.visible).toBe(true)
    fx.shieldHit('A', 1, 0, createPose())
    fx.dispose()
  })

  it('Feuer und Wirbel laufen ohne Fehler, Licht leuchtet', () => {
    for (const ult of ['SMASH', 'SURGE'] as const) {
      const fx = run(ult)
      expect(fx.glow()?.intensity).toBeGreaterThan(0)
      fx.smashImpact(0, 0, 0)
      fx.reset()
      expect(fx.glow()).toBeNull()
      fx.dispose()
    }
  })

  it('Zweiter Wind: Wirbelsturm-Trichter sichtbar, verschwindet danach', () => {
    const fx = run('SURGE')
    const tornadoA = fx.object.children[3]!
    expect(tornadoA.visible).toBe(true)
    const p = poses()
    const windows: UltWindow[] = [{ side: 'A', ult: 'SURGE', start: 1000, end: 4000, power: 1 }]
    fx.update(4100, 0.016, p, windows, false)
    expect(tornadoA.visible).toBe(false)
    fx.dispose()
  })

  it('nach dem Ende verlöschen die Effekte', () => {
    const fx = run('RUSH')
    const p = poses()
    const windows: UltWindow[] = [{ side: 'A', ult: 'RUSH', start: 1000, end: 4000, power: 1 }]
    for (let t = 4000; t < 5000; t += 16) fx.update(t, 0.016, p, windows, false)
    expect(fx.object.children[1]!.visible).toBe(false)
    expect(fx.glow()).toBeNull()
    fx.dispose()
  })
})
