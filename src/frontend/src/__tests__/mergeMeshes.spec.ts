import { describe, expect, it } from 'vitest'
import { BoxGeometry, Group, Mesh, MeshStandardMaterial } from 'three'
import { mergeByMaterial } from '@/components/three/mergeMeshes'

describe('mergeByMaterial', () => {
  it('fasst Teile mit gleichem Material zusammen und behält die Lage', () => {
    const metal = new MeshStandardMaterial({ name: 'metal' })
    const glow = new MeshStandardMaterial({ name: 'glow' })
    const root = new Group()
    for (let i = 0; i < 5; i++) {
      const m = new Mesh(new BoxGeometry(1, 1, 1), metal)
      m.position.x = i * 2
      root.add(m)
    }
    root.add(new Mesh(new BoxGeometry(1, 1, 1), glow))

    expect(mergeByMaterial(root)).toEqual({ before: 6, after: 2 })
    const merged = root.children.find((c) => (c as Mesh).material === metal) as Mesh
    merged.geometry.computeBoundingBox()
    expect(merged.geometry.boundingBox!.max.x).toBeCloseTo(8.5)
    // zweiter Aufruf ändert nichts mehr
    expect(mergeByMaterial(root)).toEqual({ before: 6, after: 2 })
    expect(root.children).toHaveLength(2)
  })
})
