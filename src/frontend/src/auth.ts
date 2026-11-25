import { computed, reactive } from 'vue'

const STORAGE_KEY = 'kreisel-kaempfer.session'

interface Session {
  token: string
  username: string
}

const storage = {
  get(): string | null {
    try {
      return globalThis.localStorage?.getItem?.(STORAGE_KEY) ?? null
    } catch {
      return null
    }
  },
  set(value: string | null) {
    try {
      if (value === null) globalThis.localStorage?.removeItem?.(STORAGE_KEY)
      else globalThis.localStorage?.setItem?.(STORAGE_KEY, value)
    } catch {}
  },
}

function load(): Session | null {
  try {
    const raw = storage.get()
    const s = raw ? (JSON.parse(raw) as Session) : null
    return s?.token && s.username ? s : null
  } catch {
    return null
  }
}

const state = reactive<{ session: Session | null }>({ session: load() })

export const auth = {
  get token() {
    return state.session?.token ?? null
  },
  get username() {
    return state.session?.username ?? ''
  },
  get loggedIn() {
    return state.session !== null
  },
}

export const username = computed(() => state.session?.username ?? '')
export const initials = computed(() => username.value.slice(0, 2).toUpperCase())

export function setSession(token: string, name: string) {
  state.session = { token, username: name }
  storage.set(JSON.stringify(state.session))
}

export function clearSession() {
  state.session = null
  storage.set(null)
}
