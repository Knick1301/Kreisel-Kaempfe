import { reactive } from 'vue'
import {
  acceptChallenge,
  acceptFriend,
  addFriend,
  challengeFriend,
  declineChallenge,
  fetchBeyblades,
  fetchFriends,
  removeFriend,
  type Challenge,
  type FightMode,
  type FriendsDto,
} from '@/api'
import { pickActive } from '@/activeBeyblade'
import { auth } from '@/auth'

const state = reactive<{ data: FriendsDto | null }>({ data: null })

/** Freunde, Anfragen und Herausforderungen des angemeldeten Spielers. Header und Profil teilen sich den Stand. */
export const friends = {
  get data(): FriendsDto | null {
    return state.data
  },
  /** offene Anfragen und Herausforderungen an mich: Punkt am Profil */
  get pending(): number {
    const d = state.data
    if (!d) return 0
    return d.incoming.length + d.challenges.filter((c) => !c.mine && !c.matchId).length
  },
  /** Herausforderungen an mich, die noch offen sind */
  get invites(): Challenge[] {
    return state.data?.challenges.filter((c) => !c.mine && !c.matchId) ?? []
  },
}

function set(d: FriendsDto) {
  state.data = d && typeof d === 'object' && 'friends' in d ? d : null
  return state.data
}

export async function refreshFriends() {
  if (!auth.loggedIn) return null
  try {
    return set(await fetchFriends())
  } catch {
    return state.data
  }
}

export function clearFriends() {
  state.data = null
}

export const sendFriendRequest = async (name: string) => set(await addFriend(name))
export const acceptFriendRequest = async (id: number) => set(await acceptFriend(id))
export const removeFriendship = async (id: number) => set(await removeFriend(id))
export const rejectChallenge = async (id: string) => set(await declineChallenge(id))

/** Aktiver Kreisel für Herausforderungen (zuletzt im Kampf benutzt, sonst der erste). */
async function activeBeyId(): Promise<number> {
  const bey = pickActive(await fetchBeyblades())
  if (!bey) throw new Error('Baue zuerst einen Kreisel im Baukasten.')
  return bey.id
}

export async function sendChallenge(username: string, mode: FightMode) {
  return set(await challengeFriend(username, await activeBeyId(), mode))
}

/** Herausforderung annehmen, liefert die Match- bzw. Raum-ID zum Hineinspringen. */
export async function takeChallenge(c: Challenge) {
  const d = set(await acceptChallenge(c.id, await activeBeyId()))
  return d?.challenges.find((x) => x.id === c.id)?.matchId ?? null
}

/** Wohin eine angenommene Herausforderung führt */
export function challengeRoute(c: Pick<Challenge, 'mode' | 'matchId'>) {
  if (!c.matchId) return null
  return c.mode === 'ARENA'
    ? { name: 'arena', query: { room: c.matchId } }
    : { name: 'kampf', query: { match: c.matchId } }
}
