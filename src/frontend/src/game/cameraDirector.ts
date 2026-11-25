import type { Choreography, FighterPose } from './choreographer'

/**
 * Broadcast-Kamera ohne three-Abhängigkeit: folgt dem Geschehen weich, zoomt bei Clashes,
 * fährt nach dem Finish auf den Sieger. Shake ist proportional zur Treffer-Intensität.
 */
export interface Vec3 {
  x: number
  y: number
  z: number
}

const BASE_AZIMUTH = 0
const FOLLOW = 2.6
const AIM = 4.5

export class CameraDirector {
  readonly position: Vec3 = { x: 0, y: 5, z: 10 }
  readonly target: Vec3 = { x: 0, y: 0.6, z: 0 }
  /** Shake-Versatz, wird zusätzlich auf die Position gelegt */
  readonly shake: Vec3 = { x: 0, y: 0, z: 0 }
  private readonly desiredPos: Vec3 = { x: 0, y: 0, z: 0 }
  private readonly desiredTarget: Vec3 = { x: 0, y: 0, z: 0 }
  private shakeAmp = 0
  private shakeTime = 0
  private nextClash = 0
  private lastT = -Infinity
  private initialized = false

  constructor(
    private readonly choreo: Choreography,
    private readonly calm: boolean,
  ) {}

  /** Übernimmt eine vom Nutzer gesetzte Ansicht, damit die Broadcast-Kamera ohne Sprung weiterfährt. */
  takeOver(position: Vec3, target: Vec3) {
    Object.assign(this.position, position)
    Object.assign(this.target, target)
    this.initialized = true
  }

  /** Zusätzlicher Ruck, z. B. wenn eine Ult auslöst. */
  kick(amount: number) {
    if (!this.calm) this.shakeAmp = Math.max(this.shakeAmp, amount)
  }

  reset() {
    this.nextClash = 0
    this.shakeAmp = 0
    this.lastT = -Infinity
    this.initialized = false
  }

  update(dtSec: number, tMs: number, a: FighterPose, b: FighterPose, winner: FighterPose) {
    if (tMs < this.lastT - 50) this.reset()
    this.lastT = tMs
    const c = this.choreo
    const t = tMs / 1000

    let az = BASE_AZIMUTH
    let el = 0.52
    let dist: number
    const tgt = this.desiredTarget

    if (tMs < 0) {
      tgt.x = 0
      tgt.y = 0.6
      tgt.z = 0
      dist = 12.5
      el = 0.62
    } else if (tMs < c.finishMs) {
      tgt.x = (a.x + b.x) / 2
      tgt.y = 0.45
      tgt.z = (a.z + b.z) / 2
      const sep = Math.hypot(a.x - b.x, a.z - b.z)
      dist = 6.6 + 0.5 * sep
      az = BASE_AZIMUTH + 0.18 * Math.sin(t * 0.21)
      el = 0.52 + 0.04 * Math.sin(t * 0.13)
      // Zoom auf nahe Clashes
      for (const clash of c.clashes) {
        const d = (tMs - clash.t) / 600
        if (d < -1 || d > 1) continue
        const bell = 1 - d * d
        const z = clash.intensity * bell * (this.calm ? 0.4 : 1)
        dist *= 1 - 0.18 * z
        tgt.x += (clash.x - tgt.x) * 0.4 * z
        tgt.z += (clash.z - tgt.z) * 0.4 * z
      }
    } else {
      const after = (tMs - c.finishMs) / 1000
      tgt.x = winner.x
      tgt.y = winner.y + 0.35
      tgt.z = winner.z
      const u = Math.min(1, after / 1.5)
      dist = 7.2 - 3.2 * u * (2 - u)
      el = 0.5 - 0.12 * u
      az = BASE_AZIMUTH + after * 0.25
    }

    // Shake beim Überschreiten eines Clash-Zeitpunkts
    while (this.nextClash < c.clashes.length && c.clashes[this.nextClash]!.t <= tMs) {
      const clash = c.clashes[this.nextClash]!
      if (!this.calm && tMs - clash.t < 200) {
        const ult = clash.ult === 'SMASH' ? 0.22 : clash.ult === 'RUSH' ? 0.04 : 0
        this.shakeAmp = Math.max(
          this.shakeAmp,
          0.13 * clash.intensity + (clash.final ? 0.14 : 0) + ult,
        )
      }
      this.nextClash++
    }
    this.shakeTime += dtSec
    this.shakeAmp *= Math.exp(-dtSec / 0.22)
    const s = this.shakeAmp
    this.shake.x = s * Math.sin(this.shakeTime * 53)
    this.shake.y = s * 0.6 * Math.sin(this.shakeTime * 71 + 1)
    this.shake.z = s * Math.sin(this.shakeTime * 61 + 2)

    const p = this.desiredPos
    p.x = tgt.x + dist * Math.cos(el) * Math.sin(az)
    p.y = tgt.y + dist * Math.sin(el)
    p.z = tgt.z + dist * Math.cos(el) * Math.cos(az)

    if (!this.initialized) {
      Object.assign(this.position, p)
      Object.assign(this.target, tgt)
      this.initialized = true
      return
    }
    const fp = 1 - Math.exp(-dtSec * FOLLOW)
    const ft = 1 - Math.exp(-dtSec * AIM)
    this.position.x += (p.x - this.position.x) * fp
    this.position.y += (p.y - this.position.y) * fp
    this.position.z += (p.z - this.position.z) * fp
    this.target.x += (tgt.x - this.target.x) * ft
    this.target.y += (tgt.y - this.target.y) * ft
    this.target.z += (tgt.z - this.target.z) * ft
  }
}
