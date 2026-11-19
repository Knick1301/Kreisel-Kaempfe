import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import TouchControls from '@/components/battle/TouchControls.vue'

function pointer(type: string, x: number, y: number) {
  const e = new Event(type, { bubbles: true, cancelable: true }) as PointerEvent
  Object.assign(e, { pointerId: 1, clientX: x, clientY: y, pointerType: 'touch' })
  return e
}

describe('TouchControls', () => {
  it('Stick meldet Richtung und Stärke, Loslassen stoppt', async () => {
    const wrapper = mount(TouchControls, { props: { ultReady: false, dashCharge: 1 } })
    const stick = wrapper.find('[data-test="stick"]')
    // jsdom: Rechteck ist 0×0 bei (0,0), Mitte also (0,0)
    stick.element.dispatchEvent(pointer('pointerdown', 0, 0))
    stick.element.dispatchEvent(pointer('pointermove', 0, -120))
    stick.element.dispatchEvent(pointer('pointerup', 0, -120))
    const moves = wrapper.emitted('move')!
    // ganz nach oben gezogen: volle Stärke ins Bild hinein (z = −1)
    const [x, z] = moves[moves.length - 2]!
    expect(x).toBeCloseTo(0, 6)
    expect(z).toBeCloseTo(-1, 6)
    expect(moves[moves.length - 1]).toEqual([0, 0])
  })

  it('Totzone: kleiner Ausschlag bewegt nicht', () => {
    const wrapper = mount(TouchControls)
    const stick = wrapper.find('[data-test="stick"]')
    stick.element.dispatchEvent(pointer('pointerdown', 0, 0))
    stick.element.dispatchEvent(pointer('pointermove', 5, 0))
    const moves = wrapper.emitted('move')!
    expect(moves[moves.length - 1]).toEqual([0, 0])
  })

  it('Dash nur aufgeladen, Ult nur bereit', async () => {
    const wrapper = mount(TouchControls, { props: { ultReady: false, dashCharge: 0.5 } })
    wrapper.find('[data-test="dash"]').element.dispatchEvent(pointer('pointerdown', 0, 0))
    wrapper.find('[data-test="ult"]').element.dispatchEvent(pointer('pointerdown', 0, 0))
    expect(wrapper.emitted('dash')).toBeUndefined()
    expect(wrapper.emitted('ult')).toBeUndefined()
    await wrapper.setProps({ ultReady: true, dashCharge: 1 })
    wrapper.find('[data-test="dash"]').element.dispatchEvent(pointer('pointerdown', 0, 0))
    wrapper.find('[data-test="ult"]').element.dispatchEvent(pointer('pointerdown', 0, 0))
    expect(wrapper.emitted('dash')).toHaveLength(1)
    expect(wrapper.emitted('ult')).toHaveLength(1)
  })
})
