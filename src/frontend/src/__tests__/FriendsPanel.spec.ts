import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import FriendsPanel from '@/components/FriendsPanel.vue'
import { clearSession, setSession } from '@/auth'
import { clearFriends } from '@/stores/friends'

const rank = (league: string) => ({
  rating: 1000,
  tier: league.split(' ')[0],
  division: 2,
  league,
  divisionFloor: 1000,
  progress: 10,
  nextLeague: null,
  pointsToNext: null,
  level: 1,
  xp: 0,
  xpLevelStart: 0,
  xpNextLevel: 50,
})

const data = {
  friends: [
    { id: 1, username: 'mia', online: true, ranking: rank('Silber II'), arenaRanking: null },
  ],
  incoming: [
    { id: 2, username: 'tom', online: false, ranking: rank('Bronze II'), arenaRanking: null },
  ],
  outgoing: [],
  challenges: [],
}

afterEach(() => {
  vi.unstubAllGlobals()
  clearFriends()
  clearSession()
})

describe('FriendsPanel', () => {
  it('zeigt Freunde mit Online-Status und Anfragen, schickt neue Anfragen', async () => {
    setSession('token', 'demo')
    const fetchMock = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>(
      async () => new Response(JSON.stringify(data)),
    )
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = mount(FriendsPanel)
    await flushPromises()

    expect(wrapper.text()).toContain('mia')
    expect(wrapper.find('.online.on').exists()).toBe(true)
    expect(wrapper.text()).toContain('Anfragen an dich')
    expect(wrapper.text()).toContain('tom')

    await wrapper.find('input').setValue('lena')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    const post = fetchMock.mock.calls.find(
      ([u, init]) => u === '/api/friends' && init?.method === 'POST',
    )
    expect(post).toBeTruthy()
    expect(JSON.parse(String(post![1]!.body))).toEqual({ username: 'lena' })
    expect(wrapper.text()).toContain('Anfrage an lena gesendet.')
  })
})
