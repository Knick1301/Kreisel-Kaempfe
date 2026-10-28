import { afterEach, describe, it, expect, vi } from 'vitest'

import { flushPromises, mount } from '@vue/test-utils'
import HomeView from '../views/HomeView.vue'

const part = (name: string) => ({
  id: 1,
  name,
  modelPath: `${name}.glb`,
  weight: 10,
  attack: 0,
  defense: 0,
  stamina: 0,
  dash: 0,
  burstResistance: 0,
})

const demoBeyblade = {
  id: 1,
  name: 'Beyblade1',
  fusionWheel: part('wheel'),
  spinTrack: part('track'),
  tip: part('tip'),
  stats: { attack: 7, defense: 8, stamina: 6, dash: 8, weight: 79, burstResistance: 2 },
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('HomeView', () => {
  it('zeigt den Beyblade aus dem Backend und die Spielmodi', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) =>
        url.startsWith('/api/')
          ? new Response(JSON.stringify([demoBeyblade]))
          : new Response(null, { status: 404 }),
      ),
    )

    const wrapper = mount(HomeView, { global: { stubs: ['RouterLink'] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Dein Beyblade')
    expect(wrapper.text()).toContain('Beyblade1')
    expect(wrapper.text()).toContain('79')
    expect(wrapper.findAll('button.mode')).toHaveLength(3)
  })

  it('meldet, wenn das Backend nicht erreichbar ist', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(null, { status: 502, statusText: 'Bad Gateway' })),
    )

    const wrapper = mount(HomeView, { global: { stubs: ['RouterLink'] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Backend nicht erreichbar')
  })
})
