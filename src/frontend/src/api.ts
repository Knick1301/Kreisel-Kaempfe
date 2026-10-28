
export interface PartDto {
  id: number
  name: string
  modelPath: string | null
  weight: number
  attack: number
  defense: number
  stamina: number
  dash: number
  burstResistance: number
}

export interface BeybladeStats {
  attack: number
  defense: number
  stamina: number
  dash: number
  weight: number
  burstResistance: number
}

export interface BeybladeDto {
  id: number
  name: string
  fusionWheel: PartDto
  spinTrack: PartDto
  tip: PartDto
  stats: BeybladeStats
}

export const CURRENT_USER = 'demo'

async function get<T>(path: string): Promise<T> {
  const res = await fetch(`/api${path}`)
  if (!res.ok) {
    throw new Error(`${res.status} ${res.statusText}`)
  }
  return res.json() as Promise<T>
}

export function fetchBeyblades(username = CURRENT_USER) {
  return get<BeybladeDto[]>(`/users/${encodeURIComponent(username)}/beyblades`)
}

export function modelUrl(modelPath: string) {
  return `/models/${modelPath}`
}
