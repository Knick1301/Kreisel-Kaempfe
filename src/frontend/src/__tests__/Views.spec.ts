import { afterEach, describe, it, expect, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import BaukastenView from '../views/BaukastenView.vue'
import RanglisteView from '../views/RanglisteView.vue'

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

describe('RanglisteView', () => {
  it('zeigt die Rangliste mit eigener Platzierung', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (url.startsWith('/api/leaderboard')) {
          return new Response(
            JSON.stringify({
              entries: [
                { rank: 1, username: 'rivale', wins: 4, losses: 1 },
                { rank: 2, username: 'demo', wins: 2, losses: 2 },
              ],
              me: { rank: 2, username: 'demo', wins: 2, losses: 2 },
              totalPlayers: 2,
              empty: false,
            }),
          )
        }
        return new Response(
          JSON.stringify({ username: 'demo', wins: 2, losses: 2, winRate: 50, rank: 2 }),
        )
      }),
    )

    const wrapper = mount(RanglisteView, { global: { stubs: ['RouterLink'] } })
    await flushPromises()

    expect(wrapper.findAll('tbody tr')).toHaveLength(2)
    expect(wrapper.find('tr.me').text()).toContain('demo')
    expect(wrapper.text()).toContain('#2')
  })

  it('zeigt „Noch keine Kämpfe“ ohne Ergebnisse', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) =>
        url.startsWith('/api/leaderboard')
          ? new Response(JSON.stringify({ entries: [], me: null, totalPlayers: 0, empty: true }))
          : new Response(
              JSON.stringify({ username: 'demo', wins: 0, losses: 0, winRate: 0, rank: null }),
            ),
      ),
    )

    const wrapper = mount(RanglisteView, { global: { stubs: ['RouterLink'] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Noch keine Kämpfe')
    expect(wrapper.find('table').exists()).toBe(false)
  })
})
