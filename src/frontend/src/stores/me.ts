import { reactive } from 'vue'
import { fetchStats, type PlayerStats, type Rank } from '@/api'
import { auth } from '@/auth'

const state = reactive<{ stats: PlayerStats | null }>({ stats: null })

/** Statistik, Liga und Level des angemeldeten Spielers. Wird nach jedem Kampf neu geladen. */
export const me = {
  get stats(): PlayerStats | null {
    return state.stats
  },
  get rank(): Rank | null {
    return state.stats?.ranking ?? null
  },
}

let pending: Promise<void> | null = null

export function refreshMe(): Promise<void> {
  if (!auth.loggedIn) return Promise.resolve()
  pending ??= fetchStats()
    .then((s) => {
      state.stats = s && typeof s === 'object' && 'ranking' in s ? s : null
    })
    .catch(() => {})
    .finally(() => {
      pending = null
    })
  return pending
}

export function clearMe() {
  state.stats = null
}
