import { auth } from '@/auth'

const key = () => `kreisel-kaempfer.active.${auth.username}`

export function getActiveId(): number | null {
  try {
    const raw = globalThis.localStorage?.getItem?.(key())
    const id = raw ? Number(raw) : NaN
    return Number.isFinite(id) ? id : null
  } catch {
    return null
  }
}

export function setActiveId(id: number | null) {
  try {
    if (id === null) globalThis.localStorage?.removeItem?.(key())
    else globalThis.localStorage?.setItem?.(key(), String(id))
  } catch {
    return
  }
}

export function pickActive<T extends { id: number }>(list: T[]): T | null {
  const id = getActiveId()
  return list.find((b) => b.id === id) ?? list[0] ?? null
}
