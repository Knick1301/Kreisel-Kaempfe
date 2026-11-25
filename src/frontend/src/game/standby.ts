import type { Side } from '@/api'
import { arenaById, bowlY } from './arenas'
import {
  LAUNCH_HEIGHT,
  LAUNCH_THETA,
  launchRestRadius,
  type Choreography,
  type FighterPose,
} from './choreographer'

/**
 * Wartestellung vor dem Anspinnen: Beide Kreisel hängen über ihrem Launcher am Rand. Mit jeder getroffenen
 * Zone dreht der eigene schneller (Schwung holen). Rein optisch, der Kampf steht da noch nicht fest. Die
 * Ruheposition ist genau der Startpunkt der echten Choreografie.
 */
export interface StandbyChoreography extends Choreography {
  /** Schwung einer Seite: 0 = Ruhe, 1 = voll aufgeladen */
  setCharge(side: Side, charge: number): void
}

const now = () => globalThis.performance?.now() ?? Date.now()

export function createStandbyChoreography(arenaId?: string): StandbyChoreography {
  const arena = arenaById(arenaId)
  const unit = launchRestRadius(arena)
  const restY = bowlY(arena, unit) + LAUNCH_HEIGHT
  const charge: Record<Side, number> = { A: 0, B: 0 }
  const speed: Record<Side, number> = { A: 0.6, B: 0.6 }
  const spin: Record<Side, number> = { A: 0, B: Math.PI / 3 }
  const last: Record<Side, number> = { A: 0, B: 0 }

  return {
    arena,
    finishMs: Infinity,
    endMs: Infinity,
    finish: 'DECISION',
    winner: 'A',
    loser: 'B',
    clashes: [],
    burst: null,
    setCharge(side, value) {
      charge[side] = Math.max(0, Math.min(1, value))
    },
    sample(side: Side, _tMs: number, out: FighterPose) {
      const t = now()
      const dt = last[side] ? Math.min(0.1, (t - last[side]) / 1000) : 0
      last[side] = t
      // Drehzahl folgt dem Schwung weich
      const target = 0.6 + 34 * charge[side] ** 1.3
      speed[side] += (target - speed[side]) * (1 - Math.exp(-dt * 5))
      spin[side] -= speed[side] * dt

      const theta = LAUNCH_THETA[side]
      // Voll aufgeladen vibriert der Kreisel leicht im Launcher
      const buzz = charge[side] >= 1 ? 0.025 : 0
      out.x = unit * Math.cos(theta)
      out.y = restY + buzz * Math.sin(t / 18)
      out.z = unit * Math.sin(theta)
      out.tiltX = buzz * Math.sin(t / 23)
      out.tiltZ = buzz * Math.cos(t / 29)
      out.spin = spin[side]
      out.burst = false
      return out
    },
    sampleParts() {
      return false
    },
  }
}
