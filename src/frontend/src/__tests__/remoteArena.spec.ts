import { describe, expect, it } from 'vitest'
import {
  RENDER_DELAY_MS,
  RemoteArena,
  type ArenaEvent,
  type BodyState,
} from '@/game/physics/remoteArena'

const body = (x: number, spin = 1): BodyState => ({
  x,
  z: 0,
  vx: 0,
  vz: 0,
  spin,
  meter: 0,
  ult: null,
  ultReady: false,
  dash: 1,
  hits: 0,
  dashes: 0,
  out: false,
})

const state = (t: number, ax: number, events: ArenaEvent[] = [], frozen = false) =>
  JSON.stringify({
    type: 'state',
    status: 'FIGHT',
    t,
    countdownMs: 0,
    frozen,
    a: body(ax),
    b: body(-ax),
    events,
  })

describe('RemoteArena', () => {
  it('interpoliert zwischen zwei Ständen mit kurzer Verzögerung', () => {
    const r = new RemoteArena('raum')
    r.receive(state(1000, 0), 0)
    r.receive(state(1100, 1), 100)
    // jetzt = Ankunft des zweiten Stands: Anzeige liegt RENDER_DELAY_MS dahinter
    const t = r.update(100)
    expect(t).toBe(1100 - RENDER_DELAY_MS)
    const k = (t - 1000) / 100
    expect(r.bodies.A.x).toBeCloseTo(k, 6)
    expect(r.bodies.B.x).toBeCloseTo(-k, 6)
  })

  it('schätzt nicht über eine eingefrorene Ult-Einblendung hinaus', () => {
    const r = new RemoteArena('raum')
    r.receive(state(2000, 0), 0)
    r.receive(state(2000, 0, [], true), 500)
    expect(r.update(900)).toBe(2000 - RENDER_DELAY_MS)
  })

  it('reicht Ereignisse und Raum-Nachrichten weiter', () => {
    const r = new RemoteArena('raum')
    const got: ArenaEvent[] = []
    let room: unknown = null
    r.onEvents = (e) => got.push(...e)
    r.onRoom = (x) => {
      room = x
    }
    const clash = {
      type: 'CLASH',
      t: 1200,
      side: 'A',
      intensity: 0.5,
      x: 0,
      z: 0,
      nx: 1,
      nz: 0,
      deflected: false,
      ult: null,
      finish: null,
    } as ArenaEvent
    r.receive(state(1200, 0, [clash]), 0)
    r.receive(JSON.stringify({ type: 'room', room: { id: 'raum' } }), 0)
    expect(got).toEqual([clash])
    expect(room).toEqual({ id: 'raum' })
  })
})
