import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import type { FighterDto } from '@/api'
import UltBadge from '@/components/battle/UltBadge.vue'

const fighter = {
  side: 'A',
  username: 'demo',
  bot: false,
  beyblade: 'Testkreisel',
  stats: { attack: 7, defense: 4, stamina: 8, dash: 5, weight: 80, burstResistance: 3 },
  style: { attack: 0.4, defense: 0.3, stamina: 0.3 },
  archetype: 'ATTACK',
  ult: { type: 'SMASH', label: 'Vernichtungsschlag' },
} as FighterDto

describe('UltBadge', () => {
  it('füllt den Ring mit der Leiste und zeigt den Zustand', async () => {
    const w = mount(UltBadge, { props: { fighter, value: 0.25 } })
    const ring = w.find('circle.progress')
    const circ = Number(ring.attributes('stroke-dasharray'))
    expect(Number(ring.attributes('stroke-dashoffset'))).toBeCloseTo(circ * 0.75)
    expect(w.text()).toContain('Vernichtungsschlag')
    expect(w.text()).toContain('25 %')
    expect(w.attributes('aria-label')).toContain('25 %')

    await w.setProps({ value: 1 })
    expect(w.classes()).toContain('full')
    expect(w.text()).toContain('Bereit')

    await w.setProps({ value: 0, active: 'SMASH' })
    expect(w.classes()).toContain('active')
    expect(Number(w.find('circle.progress').attributes('stroke-dashoffset'))).toBeCloseTo(0)
    expect(w.text()).toContain('Aktiv!')
  })

  it('wird bei voller Leiste im laufenden Kampf zum Knopf', async () => {
    const w = mount(UltBadge, { props: { fighter, value: 0.6, interactive: true } })
    expect(w.element.tagName).toBe('BUTTON')
    expect(w.attributes('disabled')).toBeDefined()
    await w.trigger('click')
    expect(w.emitted('activate')).toBeUndefined()

    await w.setProps({ value: 1 })
    expect(w.classes()).toContain('ready')
    expect(w.attributes('disabled')).toBeUndefined()
    expect(w.text()).toContain('Jetzt drücken!')
    await w.trigger('click')
    expect(w.emitted('activate')).toHaveLength(1)
  })

  it('zeigt nichts ohne Ult', () => {
    const w = mount(UltBadge, { props: { fighter: { ...fighter, ult: undefined }, value: 0.5 } })
    expect(w.find('.ult-badge').exists()).toBe(false)
  })
})
