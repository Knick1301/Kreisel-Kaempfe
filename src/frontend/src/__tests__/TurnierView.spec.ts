import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import TurnierView from '@/views/TurnierView.vue'
import { clearSession, setSession } from '@/auth'

const entry = (slot: number, name: string, you = false) => ({
  slot,
  beyblade: name,
  username: you ? 'demo' : null,
  you,
  level: you ? null : slot % 3,
})

const cup = {
  id: 'c1',
  format: 'CLASSIC',
  status: 'RUNNING',
  round: 0,
  roundName: 'Viertelfinale',
  results: [],
  upcoming: [
    { a: entry(0, 'Mein Kreisel', true), b: entry(1, 'Wilder Orkan'), winner: null, finish: null },
    { a: entry(2, 'Kalter Mond'), b: entry(3, 'Roter Falke'), winner: null, finish: null },
    { a: entry(4, 'Stiller Wolf'), b: entry(5, 'Dunkler Rabe'), winner: null, finish: null },
    { a: entry(6, 'Eherner Titan'), b: entry(7, 'Flinker Greif'), winner: null, finish: null },
  ],
  opponent: { side: 'B', username: 'KI', bot: true, beyblade: 'Wilder Orkan', archetype: 'ATTACK' },
  matchId: null,
  xpReward: 0,
}

afterEach(() => {
  vi.unstubAllGlobals()
  clearSession()
})

describe('TurnierView', () => {
  it('zeigt Baum und nächsten Gegner', async () => {
    setSession('token', 'demo')
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) =>
        url.startsWith('/api/tournament')
          ? new Response(JSON.stringify(cup))
          : new Response(JSON.stringify([])),
      ),
    )
    const wrapper = mount(TurnierView, { global: { stubs: ['RouterLink'] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Viertelfinale')
    expect(wrapper.text()).toContain('Nächster Gegner: Wilder Orkan')
    expect(wrapper.findAll('.round')).toHaveLength(3)
    expect(wrapper.findAll('.r0 .pair')).toHaveLength(4)
    expect(wrapper.find('.slot.you').text()).toContain('Mein Kreisel (Du)')
    expect(wrapper.findAll('.r2 .slot.tbd')).toHaveLength(2)
  })

  it('ohne Cup: Kreisel wählen und starten', async () => {
    setSession('token', 'demo')
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) =>
        url.startsWith('/api/tournament')
          ? new Response(JSON.stringify({ message: 'Kein laufender Cup.' }), { status: 404 })
          : new Response(JSON.stringify([{ id: 5, name: 'Blitz' }])),
      ),
    )
    const wrapper = mount(TurnierView, { global: { stubs: ['RouterLink'] } })
    await flushPromises()
    expect(wrapper.text()).toContain('Neuer Cup')
    expect(wrapper.text()).toContain('Blitz')
    expect(wrapper.find('button.cta').attributes('disabled')).toBeUndefined()
  })
})
