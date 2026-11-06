import { afterEach, describe, it, expect, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import BaukastenView from '../views/BaukastenView.vue'
import RanglisteView from '../views/RanglisteView.vue'
import { clearSession, setSession } from '../auth'
import { clearMe } from '../stores/me'

const part = (id: number, name: string) => ({
  id,
  name,
  modelPath: null,
  weight: 10,
  attack: 2,
  defense: 1,
  stamina: 0,
  dash: 0,
  burstResistance: 0,
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('BaukastenView', () => {
  it('zeigt die Teile und die Werte-Vorschau', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (url.startsWith('/api/parts/')) {
          return new Response(JSON.stringify([part(1, 'Teil A'), part(2, 'Teil B')]))
        }
        if (url.startsWith('/api/beyblades/preview')) {
          return new Response(
            JSON.stringify({
              attack: 9,
              defense: 4,
              stamina: 5,
              dash: 1,
              weight: 77,
              burstResistance: 3,
            }),
          )
        }
        if (url.startsWith('/api/me/')) return new Response(JSON.stringify([]))
        return new Response(null, { status: 404 })
      }),
    )

    const wrapper = mount(BaukastenView)
    await flushPromises()

    expect(wrapper.findAll('button.option')).toHaveLength(6)
    expect(wrapper.text()).toContain('Teil A')
    expect(wrapper.text()).toContain('77g')

    await wrapper.findAll('button.option')[1]!.trigger('click')
    expect(wrapper.findAll('button.option.active')).toHaveLength(3)
  })
})

describe('BaukastenView mit mehreren Kreiseln', () => {
  it('zeigt alle gespeicherten Kreisel und einen Neu-Button', async () => {
    const bey = (id: number, name: string) => ({
      id,
      name,
      fusionWheel: part(1, 'Teil A'),
      spinTrack: part(1, 'Teil A'),
      tip: part(1, 'Teil A'),
      stats: { attack: 1, defense: 1, stamina: 1, dash: 0, weight: 30, burstResistance: 0 },
    })
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (url.startsWith('/api/parts/')) return new Response(JSON.stringify([part(1, 'Teil A')]))
        if (url.startsWith('/api/me/beyblades')) {
          return new Response(JSON.stringify([bey(1, 'Erster'), bey(2, 'Zweiter')]))
        }
        return new Response(
          JSON.stringify({
            attack: 1,
            defense: 1,
            stamina: 1,
            dash: 0,
            weight: 30,
            burstResistance: 0,
          }),
        )
      }),
    )

    const wrapper = mount(BaukastenView)
    await flushPromises()

    const chips = wrapper.findAll('button.chip')
    expect(chips.map((c) => c.text())).toEqual(['Erster', 'Zweiter', '+ Neu'])
    expect(wrapper.find('button.chip.active').text()).toBe('Erster')

    await chips[1]!.trigger('click')
    expect(wrapper.find('button.chip.active').text()).toBe('Zweiter')
    expect((wrapper.find('.name-field input').element as HTMLInputElement).value).toBe('Zweiter')

    await wrapper.findAll('button.chip')[2]!.trigger('click')
    expect(wrapper.find('button.chip.active').text()).toBe('+ Neu')
    expect((wrapper.find('.name-field input').element as HTMLInputElement).value).toBe('Kreisel 3')
  })
})

const rank = (rating: number, tier: string, division: number | null, league: string) => ({
  rating,
  tier,
  division,
  league,
  divisionFloor: rating,
  progress: 0,
  nextLeague: null,
  pointsToNext: null,
  level: 2,
  xp: 80,
  xpLevelStart: 50,
  xpNextLevel: 200,
})

describe('RanglisteView', () => {
  afterEach(() => {
    clearSession()
    clearMe()
  })

  it('zeigt die Rangliste mit Ligen und eigener Platzierung', async () => {
    setSession('token', 'demo')
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (url.startsWith('/api/leaderboard')) {
          return new Response(
            JSON.stringify({
              entries: [
                {
                  rank: 1,
                  username: 'anna',
                  wins: 4,
                  losses: 1,
                  ranking: rank(1130, 'Silber', 3, 'Silber III'),
                },
                {
                  rank: 2,
                  username: 'demo',
                  wins: 2,
                  losses: 2,
                  ranking: rank(1010, 'Bronze', 2, 'Bronze II'),
                },
              ],
              me: {
                rank: 2,
                username: 'demo',
                wins: 2,
                losses: 2,
                ranking: rank(1010, 'Bronze', 2, 'Bronze II'),
              },
              totalPlayers: 2,
              empty: false,
            }),
          )
        }
        return new Response(
          JSON.stringify({
            username: 'demo',
            wins: 2,
            losses: 2,
            winRate: 50,
            rank: 2,
            botWins: 0,
            botLosses: 0,
            ranking: rank(1010, 'Bronze', 2, 'Bronze II'),
          }),
        )
      }),
    )

    const wrapper = mount(RanglisteView, { global: { stubs: ['RouterLink'] } })
    await flushPromises()

    expect(wrapper.findAll('tbody tr')).toHaveLength(2)
    expect(wrapper.find('tr.me').text()).toContain('demo')
    expect(wrapper.text()).toContain('#2')
    expect(wrapper.text()).toContain('Silber III')
    expect(wrapper.find('tr.me').text()).toContain('Bronze II')
    // Liga-Punkte ab 0: Rating 1010 = 60 LP
    expect(wrapper.find('tr.me td.col-wins').text()).toBe('60')
    // alle 16 Ränge, der eigene markiert
    const tiers = wrapper.findAll('.tier-list li')
    expect(tiers).toHaveLength(16)
    expect(tiers[0]!.text()).toContain('Meister')
    expect(tiers[15]!.text()).toContain('Bronze III')
    expect(tiers[15]!.text()).toContain('0')
    expect(wrapper.find('.tier-list li.current').text()).toContain('Bronze II')
  })

  it('zeigt „Noch keine Kämpfe“ ohne Ergebnisse', async () => {
    setSession('token', 'demo')
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) =>
        url.startsWith('/api/leaderboard')
          ? new Response(JSON.stringify({ entries: [], me: null, totalPlayers: 0, empty: true }))
          : new Response(
              JSON.stringify({
                username: 'demo',
                wins: 0,
                losses: 0,
                winRate: 0,
                rank: null,
                ranking: rank(950, 'Bronze', 3, 'Bronze III'),
              }),
            ),
      ),
    )

    const wrapper = mount(RanglisteView, { global: { stubs: ['RouterLink'] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Noch keine Kämpfe')
    expect(wrapper.find('table').exists()).toBe(false)
  })
})
