import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { BattleReport, LaunchChallenge, LaunchInput, MatchDto } from '@/api'
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

const challenge: LaunchChallenge = {
  windowMs: 3000,
  maxTaps: 1,
  periodMs: 1400,
  zones: [0.2, 0.5, 0.8],
  zoneHalf: 0.05,
  coreHalf: 0.014,
  edgeScore: 0.5,
  perfectQuality: 0.97,
}

function matchOf(report: BattleReport, done: boolean): MatchDto {
  const launch = {
    quality: 1,
    hits: 3,
    cores: 3,
    landingX: -0.45,
    landingY: 0,
    perfect: true,
    missed: false,
  }
  const withLaunch: BattleReport = {
    ...report,
    fighters: [
      { ...report.fighters![0], launch },
      {
        ...report.fighters![1],
        launch: { ...launch, quality: 0.6, hits: 2, cores: 0, perfect: false },
      },
    ],
  }
  return {
    id: 'm1',
    mode: 'BOT',
    status: done ? 'DONE' : 'LAUNCH',
    yourSide: 'A',
    arena: report.arena!,
    fighters: report.fighters!,
    matchup: report.matchup!,
    launch: challenge,
    youLaunched: done,
    opponentReady: true,
    expiresInMs: done ? 0 : 120_000,
    ...(done ? { battleId: report.id, report: withLaunch } : {}),
  }
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
  it('zieht einen KI-Gegner, lässt anspinnen und zeigt nach dem Überspringen das Ergebnis', async () => {
    setSession('token', 'demo')
    const report = battles.RING_OUT!
    let requested: unknown = null
    let launched: LaunchInput | null = null
    const fetchMock = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>(
      async (url, init) => {
        if (url === '/api/me/beyblades') return new Response(JSON.stringify([bey]))
        if (url === '/api/matches' && init?.method === 'POST') {
          requested = JSON.parse(String(init.body))
          return new Response(JSON.stringify(matchOf(report, false)), { status: 201 })
        }
        if (url === '/api/matches/m1/launch' && init?.method === 'POST') {
          launched = JSON.parse(String(init.body))
          return new Response(JSON.stringify(matchOf(report, true)))
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
    // Arena-Wahl als eigenes Panel: Karten, Look und Spielwerte
    const cards = wrapper.findAll('.arena-picker .card')
    expect(cards.map((c) => c.find('strong').text())).toEqual([
      'Standard-Stadion',
      'Stahl-Oktagon',
      'Zyklon-Kessel',
      'Gletscher',
    ])
    await cards[2]!.trigger('click')
    expect(wrapper.find('.arena-picker .card.active').text()).toContain('Zyklon-Kessel')
    const looks = wrapper.findAll('.arena-picker .look')
    expect(looks.map((l) => l.text())).toEqual(['Sturmauge', 'Sandsturm'])
    await looks[1]!.trigger('click')
    expect(wrapper.find('.arena-picker .traits').text()).toContain('Ring-Outs')
    // Pfeiltaste wechselt die Arena
    await wrapper.find('.arena-picker .cards').trigger('keydown', { key: 'ArrowDown' })
    expect(wrapper.find('.arena-picker .card.active').text()).toContain('Gletscher')
    await wrapper.find('.arena-picker .cards').trigger('keydown', { key: 'ArrowUp' })
    expect(wrapper.find('.cta-sub').text()).toContain('Zyklon-Kessel · Sandsturm')

    await wrapper.find('button.cta').trigger('click')
    await vi.advanceTimersByTimeAsync(1300)
    await flushPromises()

    expect(requested).toEqual({ beybladeId: 1, arenaId: 'zyklon', variantId: 'sand' })
    const opponent = report.fighters![1].beyblade
    expect(wrapper.text()).toContain(opponent)
    expect(wrapper.text()).toContain('KI-Gegner')
    expect(wrapper.find('.cta').text()).toContain('Anspinnen')

    // Anspinnen: Zeiger läuft, einmal spannen (Leertaste halten) und nach rechts loslassen
    await wrapper.find('button.cta').trigger('click')
    await flushPromises()
    expect(wrapper.find('.launchpad').exists()).toBe(true)
    expect(wrapper.findAll('.launchpad .zone')).toHaveLength(3)
    await vi.advanceTimersByTimeAsync(900 + 300)
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }))
    window.dispatchEvent(new KeyboardEvent('keydown', { code: 'Space', key: ' ' }))
    await vi.advanceTimersByTimeAsync(600)
    // Halten allein startet noch nicht, die Spannung steigt
    expect(launched).toBeNull()
    expect(wrapper.findAll('.launchpad .mark')).toHaveLength(0)
    expect(wrapper.find('.launchpad .meter.power').text()).toMatch(/%/)
    window.dispatchEvent(new KeyboardEvent('keyup', { code: 'Space', key: ' ' }))
    await vi.advanceTimersByTimeAsync(50)
    await flushPromises()

    expect(launched).not.toBeNull()
    expect(launched!.aim).toBe(1)
    // ein Loslassen, gut zwei Drittel gespannt, Tastatur-Tempo
    const { taps, pull, speed } = launched!
    expect(taps).toHaveLength(1)
    expect(taps[0]!).toBeGreaterThan(0)
    expect(pull!).toBeGreaterThan(0.6)
    expect(pull!).toBeLessThan(0.75)
    expect(speed).toBe(0.6)
    // sichtbar: Markierung im Balken und kurzer Text darüber
    expect(wrapper.findAll('.launchpad .mark')).toHaveLength(1)
    expect(wrapper.findAll('.launchpad .popup').length).toBeGreaterThan(0)
    await vi.advanceTimersByTimeAsync(400)
    expect(wrapper.find('.launchpad .banner').exists()).toBe(true)

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
    expect(dialog.text()).toContain('Anspinnen')
    expect(dialog.text()).toContain('Perfekt!')
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
