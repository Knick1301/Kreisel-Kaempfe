import { MeshStandardMaterial, type Material, type Mesh, type Object3D } from 'three'

/**
 * Macht die GLB-Teile metallisch (wie in der Showcase-Szene). Liefert die neu erzeugten Materialien,
 * damit der Aufrufer sie beim Unmount freigeben kann.
 */
export function makeMetallic(root: Object3D | undefined): Material[] {
  const created: Material[] = []
  root?.traverse((obj) => {
    const mesh = obj as Mesh
    if (!mesh.isMesh) return
    const mats = Array.isArray(mesh.material) ? mesh.material : [mesh.material]
    const next = mats.map((m) => {
      if (!(m instanceof MeshStandardMaterial) || m.transparent || m.opacity < 1) return m
      const c = m.clone()
      c.metalness = Math.max(c.metalness, 0.85)
      c.roughness = Math.min(Math.max(c.roughness, 0.22), 0.38)
      created.push(c)
      return c
    })
    mesh.material = Array.isArray(mesh.material) ? next : next[0]!
  })
  return created
}
