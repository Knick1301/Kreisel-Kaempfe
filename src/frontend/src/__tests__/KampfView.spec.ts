import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { BattleReport } from '@/api'
import { setSession } from '@/auth'
import KampfView from '../views/KampfView.vue'
import fixtures from './fixtures/battles.json'

const battles = fixtures as unknown as Record<string, BattleReport>

const part = (name: string) => ({
  id: 1,
  name,
  modelPath: null,
  weight: 26,
  attack: 2,
  defense: 2,
  stamina: 2,
  dash: 2,
  burstResistance: 1,
})

const bey = {
  id: 1,
  name: 'Beyblade1',
  fusionWheel: part('Sturmklinge'),
  spinTrack: part('Schwerring'),
  tip: part('Flach'),
  stats: { attack: 7, defense: 8, stamina: 6, dash: 8, weight: 79, burstResistance: 2 },
}

function router() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/kampf', name: 'kampf', component: KampfView },
      { path: '/kampf/:id(\\d+)', name: 'kampf-replay', component: KampfView },
      { path: '/baukasten', component: { template: '<div />' } },
    ],
  })
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.useRealTimers()
})

describe('KampfView', () => {
  it('zieht einen KI-Gegner, zeigt das Intro und nach dem Überspringen das Ergebnis', async () => {
    setSession('token', 'demo')
    const report = battles.RING_OUT!
    let requested: unknown = null
    const fetchMock = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>(
      async (url, init) => {
        if (url === '/api/me/beyblades') return new Response(JSON.stringify([bey]))
        if (url === '/api/battles/bot' && init?.method === 'POST') {
          requested = JSON.parse(String(init.body))
          return new Response(JSON.stringify(report), { status: 201 })
        }
        return new Response(null, { status: 404 })
      },
    )
    vi.stubGlobal('fetch', fetchMock)
    vi.useFakeTimers()

    const r = router()
    await r.push('/kampf')
    const wrapper = mount(KampfView, { global: { plugins: [r] } })
    await vi.runOnlyPendingTimersAsync()
    await flushPromises()

    expect(wrapper.text()).toContain('Beyblade1')
    expect(wrapper.text()).toContain('? ? ?')

    await wrapper.find('button.cta').trigger('click')
    await vi.advanceTimersByTimeAsync(1300)
    await flushPromises()

    expect(requested).toEqual({ beybladeId: 1, arenaId: 'standard' })
    const opponent = report.fighters![1].beyblade
    expect(wrapper.text()).toContain(opponent)
    expect(wrapper.text()).toContain('KI-Gegner')
    expect(wrapper.find('.cta').text()).toContain('Kampf starten')

    await wrapper.find('button.cta').trigger('click')
    await vi.advanceTimersByTimeAsync(3000)
    await flushPromises()
    // ohne WebGL läuft der Kampf als Text
    expect(wrapper.find('.battle-log').exists()).toBe(true)

    await wrapper.find('button.skip').trigger('click')
    await flushPromises()

    const dialog = wrapper.find('[role="dialog"]')
    expect(dialog.exists()).toBe(true)
    expect(dialog.text()).toMatch(report.winnerSide === 'A' ? 'Sieg' : 'Niederlage')
    expect(dialog.text()).toContain('Ring-Out')
    expect(dialog.text()).toContain('Treffer')
    expect(wrapper.find('.battle-log').text()).toContain('Ring-Out!')
    wrapper.unmount()
  })

  it('zeigt alte Kämpfe ohne Timeline direkt als Ergebnis', async () => {
    setSession('token', 'demo')
    const old: BattleReport = {
      id: 7,
      winner: 'demo',
      loser: 'rivale',
      winnerBeyblade: 'Beyblade1',
      loserBeyblade: 'Bollwerk-Kern',
      finish: 'SPIN_OUT',
      rounds: 12,
      playedAt: '2026-01-01T00:00:00Z',
    }
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) =>
        url === '/api/battles/7'
          ? new Response(JSON.stringify(old))
          : new Response(null, { status: 404 }),
      ),
    )
    const r = router()
    await r.push('/kampf/7')
    const wrapper = mount(KampfView, { global: { plugins: [r] } })
    await flushPromises()

    const dialog = wrapper.find('[role="dialog"]')
    expect(dialog.text()).toContain('Sieg')
    expect(dialog.text()).toContain('Runde 12')
    expect(dialog.text()).toContain('keine Wiedergabe')
    wrapper.unmount()
  })
})
