let cached: boolean | undefined

export function hasWebGL() {
  if (cached === undefined) {
    try {
      const c = document.createElement('canvas')
      cached = !!(c.getContext('webgl2') ?? c.getContext('webgl'))
    } catch {
      cached = false
    }
  }
  return cached
}
