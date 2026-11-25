import { describe, expect, it } from 'vitest'
import { Box3, Mesh, Vector3 } from 'three'
import { TIP_DESIGNS, TRACK_DESIGNS, WHEEL_DESIGNS } from '@/game/partDesigns'
import {
  buildTip,
  buildTrack,
  buildWheel,
  MaterialCache,
  stackHeights,
} from '@/components/three/parts/partBuilders'

function size(group: import('three').Group) {
  return new Box3().setFromObject(group).getSize(new Vector3())
}

describe('Prozedurale Teile', () => {
  const mats = new MaterialCache()

  it('baut jedes Fusion Wheel detailliert und im Rahmen', () => {
    for (const d of Object.values(WHEEL_DESIGNS)) {
      const part = buildWheel(d, mats)
      const s = size(part.group)
      expect(s.x).toBeGreaterThan(1.6)
      expect(s.x).toBeLessThan(2.05)
      expect(s.y).toBeLessThan(0.45)
      let meshes = 0
      part.group.traverse((o) => {
        if ((o as Mesh).isMesh) meshes++
      })
      expect(meshes).toBeGreaterThanOrEqual(8)
      part.dispose()
    }
  })

  it('baut jeden Spin Track und jede Tip schmaler als das Wheel', () => {
    for (const d of Object.values(TRACK_DESIGNS)) {
      const part = buildTrack(d, mats)
      expect(size(part.group).x).toBeLessThan(1.45)
      expect(size(part.group).y).toBeCloseTo(part.height, 0)
      part.dispose()
    }
    for (const d of Object.values(TIP_DESIGNS)) {
      const part = buildTip(d, mats)
      expect(size(part.group).x).toBeLessThan(0.62)
      part.dispose()
    }
  })

  it('stapelt Tip, Track und Wheel ohne Lücke, Spitze unten', () => {
    for (const d of Object.values(TRACK_DESIGNS)) {
      const h = stackHeights(d)
      expect(h.tipY).toBeLessThan(h.trackY)
      expect(h.trackY).toBeLessThan(h.wheelY)
      expect(h.total).toBeLessThan(1.1)
    }
  })

  it('teilt Materialien und gibt sie frei', () => {
    const cache = new MaterialCache()
    expect(cache.get('metal', '#ffffff')).toBe(cache.get('metal', '#ffffff'))
    expect(cache.get('metal', '#ffffff')).not.toBe(cache.get('glow', '#ffffff'))
    cache.dispose()
  })
})
