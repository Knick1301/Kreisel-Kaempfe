import type { FinishType, Side } from '@/api'
import { BEY_SIZE, bowlY, pocketFloorY, type ArenaDefinition } from '@/game/arenas'
import type {
  ClashMark,
  Choreography,
  FighterPose,
  LandingMark,
  PartPose,
} from '@/game/choreographer'
import type { ArenaBodies, ArenaEvent } from './remoteArena'

/**
 * Adapter: zeigt die Live-Physik über das vorhandene `Choreography`-Interface. So laufen 3D-Szene, Effekte,
 * Treffer-Anzeigen und Kamera unverändert weiter. Die Szene fragt immer den aktuellen Stand ab, die Zeit in
 * `sample` wird nur für Spin und Animation genutzt.
 */
export class LiveChoreography implements Choreography {
  readonly clashes: ClashMark[] = []
  readonly landings: LandingMark[] = []
  burst: { t: number; x: number; y: number; z: number } | null = null
  finishMs = Number.POSITIVE_INFINITY
  endMs = Number.POSITIVE_INFINITY
  finish: FinishType = 'DECISION'
  winner: Side = 'A'
  loser: Side = 'B'
  private readonly spinAngle: Record<Side, number> = { A: 0, B: 0 }
  private lastT = 0
  private fallStart: Record<Side, number> = { A: -1, B: -1 }

  constructor(
    readonly arena: ArenaDefinition,
    private readonly sim: ArenaBodies,
  ) {}

  /** Ereignisse der Physik übernehmen (Treffer für Effekte und Kamera, Finish). */
  absorb(events: readonly ArenaEvent[]) {
    for (const e of events) {
      if (e.type === 'CLASH') {
        this.clashes.push({
          t: e.t,
          x: e.x,
          y: bowlY(this.arena, Math.hypot(e.x, e.z)) + 0.35,
          z: e.z,
          nx: e.nx,
          nz: e.nz,
          intensity: e.intensity,
          deflected: e.deflected,
          attacker: e.side,
          final: false,
          ult: e.ult ?? undefined,
        })
      } else if (e.type === 'FINISH' && e.finish) {
        this.finish = e.finish
        this.winner = e.side
        this.loser = e.side === 'A' ? 'B' : 'A'
        this.finishMs = e.t
        this.endMs = e.t + 3200
        const last = this.clashes[this.clashes.length - 1]
        if (last && e.t - last.t < 50 && (e.finish === 'BURST' || e.finish === 'RING_OUT'))
          last.final = true
        if (e.finish === 'BURST') {
          const v = this.sim.bodies[this.loser]
          this.burst = { t: e.t, x: v.x, y: bowlY(this.arena, Math.hypot(v.x, v.z)), z: v.z }
        }
        if (e.finish === 'RING_OUT') this.fallStart[this.loser] = e.t
      }
    }
  }

  sample(side: Side, tMs: number, out: FighterPose): FighterPose {
    const b = this.sim.bodies[side]
    const dt = Math.max(0, Math.min(0.1, (tMs - this.lastT) / 1000))
    if (side === 'B') this.lastT = tMs
    const spin = Math.max(0, b.spin)
    const stopped = this.finishMs < tMs && side === this.loser && this.finish === 'SPIN_OUT'
    this.spinAngle[side] += (stopped ? 0 : 6 + 40 * spin) * dt
    const r = Math.hypot(b.x, b.z)
    out.x = b.x
    out.z = b.z
    out.y = bowlY(this.arena, Math.min(r, this.arena.radius))
    if (this.fallStart[side] >= 0) {
      // Ring-Out: weiter nach außen in die Tasche und runterfallen
      const s = Math.min(1, (tMs - this.fallStart[side]) / 600)
      const k = 1 + 0.25 * s
      out.x = b.x * k
      out.z = b.z * k
      out.y = out.y + (pocketFloorY(this.arena) - out.y) * s
    }
    // Neigung: in Fahrtrichtung, bei wenig Spin taumeln
    const sp = Math.hypot(b.vx, b.vz)
    const lean = Math.min(0.25, sp * 0.03)
    const wob = 0.02 + 0.35 * Math.max(0, 0.35 - spin)
    const psi = tMs / 160
    out.tiltX = (sp > 0.01 ? (b.vz / sp) * lean : 0) + Math.sin(psi) * wob
    out.tiltZ = (sp > 0.01 ? (-b.vx / sp) * lean : 0) + Math.cos(psi) * wob
    if (stopped) {
      // ausgedreht: umgekippt liegen bleiben
      const s = Math.min(1, (tMs - this.finishMs) / 700)
      out.tiltX = 1.2 * s
      out.tiltZ = 0.3 * s
    }
    out.spin = this.spinAngle[side]
    out.burst = this.burst !== null && side === this.loser
    return out
  }

  /** Burst: Die drei Teile fliegen ballistisch auseinander und bleiben auf dem Boden liegen. */
  sampleParts(tMs: number, out: PartPose[]): boolean {
    if (!this.burst) return false
    const age = Math.max(0, (tMs - this.burst.t) / 1000)
    const dirs = [
      [1, 0.2],
      [-0.6, 0.9],
      [-0.5, -0.9],
    ] as const
    out.forEach((p, i) => {
      const [dx, dz] = dirs[i]!
      const flight = Math.min(age, 1.1)
      const speed = 2.2 + i * 0.4
      p.x = dx * speed * flight * BEY_SIZE * 0.5
      p.z = dz * speed * flight * BEY_SIZE * 0.5
      p.y = Math.max(-0.3, 2.6 * flight - 4.5 * flight * flight)
      p.rx = age * (3 + i)
      p.ry = age * (5 - i)
      p.rz = age * 2
    })
    return true
  }
}
