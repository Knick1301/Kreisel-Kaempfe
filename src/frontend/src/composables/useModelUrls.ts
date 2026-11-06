import { ref, watchEffect, type Ref } from 'vue'
import { modelUrl, type PartDto } from '@/api'

async function exists(url: string) {
  try {
    const res = await fetch(url, { method: 'HEAD' })
    return res.ok && !res.headers.get('content-type')?.includes('text/html')
  } catch {
    return false
  }
}

export function useModelUrls(parts: () => PartDto[]): Ref<string[] | null | undefined> {
  const urls = ref<string[] | null | undefined>(undefined)

  watchEffect(async () => {
    const paths = parts().map((p) => p.modelPath)
    if (paths.length === 0 || paths.some((p) => !p)) {
      urls.value = null
      return
    }
    urls.value = undefined
    const candidates = paths.map((p) => modelUrl(p!))
    const found = await Promise.all(candidates.map(exists))
    urls.value = found.every(Boolean) ? candidates : null
  })

  return urls
}
