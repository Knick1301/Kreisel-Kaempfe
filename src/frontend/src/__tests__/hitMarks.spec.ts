import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import type { BattleReport } from '@/api'
import type { ClashMark } from '@/game/choreographer'
import { CRITICAL_HIT, GRAZE, hitKind } from '@/game/hitKind'
import HitMarks from '@/components/battle/HitMarks.vue'

const clash = (o: Partial<ClashMark>): ClashMark => ({
  t: 1000,
  x: 0,
  y: 0,
  z: 0,
  nx: 1,
  nz: 0,
  intensity: 0.5,
  deflected: false,
  attacker: 'A',
  final: false,
  ...o,
})

describe('Treffer-Anzeigen', () => {
  it('ordnet Treffer ein', () => {
    expect(hitKind(clash({ ult: 'SMASH', intensity: 0.4 }))).toBe('smash')
    expect(hitKind(clash({ deflected: true, intensity: 0.9 }))).toBe('blocked')
    expect(hitKind(clash({ intensity: CRITICAL_HIT }))).toBe('critical')
    expect(hitKind(clash({ intensity: 0.5 }))).toBe('hit')
    expect(hitKind(clash({ intensity: GRAZE - 0.01 }))).toBe('graze')
  })

  it('zeigt Art und Spinverlust des Getroffenen, eingefärbt aus meiner Sicht', async () => {
    const report = {
      timeline: {
        sampleMs: 100,
        energy: { A: Array(30).fill(0.9), B: [...Array(10).fill(0.8), ...Array(20).fill(0.68)] },
        hits: { A: 1, B: 0 },
        events: [],
      },
    } as unknown as BattleReport
    const w = mount(HitMarks, { props: { report, me: 'A' } })
    w.vm.add(clash({ intensity: 0.9 }), 0.5, 0.4)
    await w.vm.$nextTick()
    const mark = w.find('.mark')
    expect(mark.text()).toContain('Kritisch!')
    expect(mark.text()).toContain('−12 %')
    expect(mark.classes()).toContain('good')
    expect(mark.attributes('style')).toContain('left: 50%')

    w.vm.add(clash({ attacker: 'B', deflected: true }), 0.3, 0.3)
    await w.vm.$nextTick()
    const blocked = w.findAll('.mark')[1]!
    expect(blocked.text()).toBe('Abgewehrt!')
    expect(blocked.classes()).toContain('good')
  })
})
