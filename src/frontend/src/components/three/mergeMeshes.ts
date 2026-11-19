import { Matrix4, Mesh, type BufferGeometry, type Material, type Object3D } from 'three'
import { mergeGeometries } from 'three/examples/jsm/utils/BufferGeometryUtils.js'

/**
 * Fasst alle Meshes eines Modells mit demselben Material zu einem Mesh zusammen (weniger Draw Calls).
 * Die Teilmodelle bestehen aus Dutzenden Einzelteilen, die sich nie gegeneinander bewegen. Modelle mit
 * Skinning oder Mehrfach-Materialien bleiben unverändert. Läuft pro Szene nur einmal.
 *
 * @returns Anzahl der Meshes vorher und nachher, für Tests
 */
export function mergeByMaterial(root: Object3D): { before: number; after: number } {
  if (root.userData.merged) return root.userData.merged
  const meshes: Mesh[] = []
  let skinned = false
  root.traverse((o) => {
    const m = o as Mesh
    if ((o as { isSkinnedMesh?: boolean }).isSkinnedMesh) skinned = true
    else if (m.isMesh) meshes.push(m)
  })
  const result = { before: meshes.length, after: meshes.length }
  root.userData.merged = result
  if (skinned || meshes.length < 2) return result

  root.updateMatrixWorld(true)
  const toRoot = new Matrix4().copy(root.matrixWorld).invert()
  const groups = new Map<Material, Mesh[]>()
  for (const m of meshes) {
    if (Array.isArray(m.material)) continue
    const list = groups.get(m.material) ?? []
    list.push(m)
    groups.set(m.material, list)
  }

  let after = meshes.length
  for (const [material, list] of groups) {
    if (list.length < 2) continue
    const geometries: BufferGeometry[] = []
    for (const m of list) {
      const g = m.geometry.clone()
      g.applyMatrix4(new Matrix4().multiplyMatrices(toRoot, m.matrixWorld))
      geometries.push(g)
    }
    // Nur Attribute, die alle Teile haben; alle indiziert oder alle nicht
    const common = Object.keys(geometries[0]!.attributes).filter((k) =>
      geometries.every((g) => g.attributes[k]),
    )
    const indexed = geometries.every((g) => g.index)
    const prepared = geometries.map((g) => {
      const h = indexed ? g : g.index ? g.toNonIndexed() : g
      for (const k of Object.keys(h.attributes)) if (!common.includes(k)) h.deleteAttribute(k)
      h.morphAttributes = {}
      return h
    })
    const merged = mergeGeometries(prepared, false)
    prepared.forEach((g) => g.dispose())
    if (!merged) continue
    const mesh = new Mesh(merged, material)
    mesh.name = `merged:${material.name}`
    mesh.castShadow = list.some((m) => m.castShadow)
    mesh.receiveShadow = list.some((m) => m.receiveShadow)
    for (const m of list) m.removeFromParent()
    root.add(mesh)
    after -= list.length - 1
  }
  result.after = after
  return result
}
