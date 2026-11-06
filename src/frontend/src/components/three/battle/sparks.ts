import {
  AdditiveBlending,
  BufferAttribute,
  BufferGeometry,
  LineBasicMaterial,
  LineSegments,
} from 'three'

/**
 * Gepoolte Funken als Liniensegmente (ein Draw-Call, keine Allokation pro Frame).
 * Jeder Funke ist ein kurzer Strich in Flugrichtung, der ausglüht.
 */
export class SparkPool {
  readonly object: LineSegments
  private readonly geometry = new BufferGeometry()
  private readonly material = new LineBasicMaterial({
    vertexColors: true,
    transparent: true,
    blending: AdditiveBlending,
    depthWrite: false,
  })
  private readonly positions: Float32Array
  private readonly colors: Float32Array
  private readonly head: Float32Array
  private readonly vel: Float32Array
  private readonly life: Float32Array
  private readonly maxLife: Float32Array
  private next = 0

  constructor(private readonly count = 240) {
    this.positions = new Float32Array(count * 6)
    this.colors = new Float32Array(count * 6)
    this.head = new Float32Array(count * 3)
    this.vel = new Float32Array(count * 3)
    this.life = new Float32Array(count)
    this.maxLife = new Float32Array(count).fill(1)
    this.geometry.setAttribute('position', new BufferAttribute(this.positions, 3))
    this.geometry.setAttribute('color', new BufferAttribute(this.colors, 3))
    this.object = new LineSegments(this.geometry, this.material)
    this.object.frustumCulled = false
    this.object.renderOrder = 2
  }

  /** Funkenregen am Kontaktpunkt, quer zur Stoßrichtung (nx, nz). */
  spawn(
    x: number,
    y: number,
    z: number,
    nx: number,
    nz: number,
    intensity: number,
    rng: () => number,
  ) {
    const n = Math.round(10 + 46 * intensity)
    for (let i = 0; i < n; i++) {
      const s = this.next
      this.next = (this.next + 1) % this.count
      const side = rng() < 0.5 ? -1 : 1
      const spread = (rng() - 0.5) * 1.6
      const speed = 3 + 7 * intensity * (0.5 + rng())
      // tangential zur Kontaktfläche, etwas nach oben
      const tx = -nz * side + nx * spread
      const tz = nx * side + nz * spread
      const len = Math.hypot(tx, tz) || 1
      this.head[s * 3] = x
      this.head[s * 3 + 1] = y
      this.head[s * 3 + 2] = z
      this.vel[s * 3] = (tx / len) * speed
      this.vel[s * 3 + 1] = 1 + rng() * 3.5
      this.vel[s * 3 + 2] = (tz / len) * speed
      this.maxLife[s] = 0.25 + rng() * 0.4
      this.life[s] = this.maxLife[s]!
    }
  }

  update(dt: number) {
    const p = this.positions
    const c = this.colors
    for (let s = 0; s < this.count; s++) {
      const o6 = s * 6
      if (this.life[s]! <= 0) {
        if (c[o6] !== 0) c.fill(0, o6, o6 + 6)
        continue
      }
      this.life[s] = this.life[s]! - dt
      const o3 = s * 3
      this.vel[o3 + 1] = this.vel[o3 + 1]! - 9 * dt
      const drag = Math.exp(-dt * 2.5)
      this.vel[o3] = this.vel[o3]! * drag
      this.vel[o3 + 2] = this.vel[o3 + 2]! * drag
      this.head[o3] = this.head[o3]! + this.vel[o3]! * dt
      this.head[o3 + 1] = this.head[o3 + 1]! + this.vel[o3 + 1]! * dt
      this.head[o3 + 2] = this.head[o3 + 2]! + this.vel[o3 + 2]! * dt
      const k = Math.max(0, this.life[s]! / this.maxLife[s]!)
      p[o6] = this.head[o3]!
      p[o6 + 1] = this.head[o3 + 1]!
      p[o6 + 2] = this.head[o3 + 2]!
      p[o6 + 3] = this.head[o3]! - this.vel[o3]! * 0.03
      p[o6 + 4] = this.head[o3 + 1]! - this.vel[o3 + 1]! * 0.03
      p[o6 + 5] = this.head[o3 + 2]! - this.vel[o3 + 2]! * 0.03
      const glow = k * k
      c[o6] = glow
      c[o6 + 1] = 0.85 * glow
      c[o6 + 2] = 0.55 * glow
      c[o6 + 3] = 0.6 * glow
      c[o6 + 4] = 0.35 * glow
      c[o6 + 5] = 0.15 * glow
    }
    this.geometry.attributes.position!.needsUpdate = true
    this.geometry.attributes.color!.needsUpdate = true
  }

  clear() {
    this.life.fill(0)
    this.colors.fill(0)
    this.geometry.attributes.color!.needsUpdate = true
  }

  dispose() {
    this.geometry.dispose()
    this.material.dispose()
  }
}
