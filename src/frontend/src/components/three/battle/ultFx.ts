import {
  AdditiveBlending,
  BufferAttribute,
  BufferGeometry,
  Color,
  CylinderGeometry,
  DoubleSide,
  Group,
  Mesh,
  Points,
  ShaderMaterial,
  SphereGeometry,
  Vector3,
} from 'three'
import { LineMaterial } from 'three/examples/jsm/lines/LineMaterial.js'
import { LineSegments2 } from 'three/examples/jsm/lines/LineSegments2.js'
import { LineSegmentsGeometry } from 'three/examples/jsm/lines/LineSegmentsGeometry.js'
import type { Side, UltType } from '@/api'
import { BEY_SIZE } from '@/game/arenas'
import type { FighterPose } from '@/game/choreographer'
import type { UltWindow } from '@/game/playback'

/**
 * Ult-Effekte in der Arena, echte Partikel statt gezeichneter Ringe:
 * - Vernichtungsschlag: Der Kreisel fängt Feuer, Glut fliegt, Energie wird beim Ausholen eingesogen.
 * - Ansturm: Blitze zucken um den Kreisel, Funken und eine leuchtende Spur.
 * - Schildwall: Energie-Kuppel mit Fresnel-Rand und Hex-Muster, Wellen bei Treffern.
 * - Zweiter Wind: grüner Wirbelsturm, der um den Kreisel aufsteigt.
 * Beim Auslösen jeder Ult explodiert ein Partikelring nach außen.
 */

type RGB = readonly [number, number, number]

const R = BEY_SIZE / 2
/** Höhe eines Kreisels in der Arena (Modelle sind etwa 0,7 so hoch wie breit) */
const BEY_HEIGHT = BEY_SIZE * 0.72

// ---------------------------------------------------------------- Partikel

interface Emit {
  x: number
  y: number
  z: number
  vx: number
  vy: number
  vz: number
  life: number
  size: number
  c0: RGB
  c1: RGB
  /** Auftrieb (Einheiten/s²) */
  lift?: number
  /** Luftwiderstand pro Sekunde */
  drag?: number
  /** Wirbel um (cx, cz) mit dieser Winkelgeschwindigkeit (rad/s) */
  swirl?: number
  cx?: number
  cz?: number
}

/** Gepoolte Leuchtpartikel: weiche Punkte, Farbe und Größe über die Lebensdauer. */
class ParticleField {
  readonly object: Points
  private readonly geo = new BufferGeometry()
  readonly material: ShaderMaterial
  private readonly pos: Float32Array
  private readonly vel: Float32Array
  private readonly c0: Float32Array
  private readonly c1: Float32Array
  private readonly prog: Float32Array
  private readonly size: Float32Array
  private readonly life: Float32Array
  private readonly age: Float32Array
  private readonly lift: Float32Array
  private readonly drag: Float32Array
  private readonly swirl: Float32Array
  private readonly center: Float32Array
  private next = 0

  constructor(private readonly max: number) {
    this.pos = new Float32Array(max * 3)
    this.vel = new Float32Array(max * 3)
    this.c0 = new Float32Array(max * 3)
    this.c1 = new Float32Array(max * 3)
    this.prog = new Float32Array(max).fill(1)
    this.size = new Float32Array(max)
    this.life = new Float32Array(max).fill(1)
    this.age = new Float32Array(max).fill(1)
    this.lift = new Float32Array(max)
    this.drag = new Float32Array(max)
    this.swirl = new Float32Array(max)
    this.center = new Float32Array(max * 2)
    this.geo.setAttribute('position', new BufferAttribute(this.pos, 3))
    this.geo.setAttribute('aColor0', new BufferAttribute(this.c0, 3))
    this.geo.setAttribute('aColor1', new BufferAttribute(this.c1, 3))
    this.geo.setAttribute('aProgress', new BufferAttribute(this.prog, 1))
    this.geo.setAttribute('aSize', new BufferAttribute(this.size, 1))
    this.material = new ShaderMaterial({
      uniforms: { uScale: { value: 400 } },
      vertexShader: /* glsl */ `
        attribute vec3 aColor0;
        attribute vec3 aColor1;
        attribute float aProgress;
        attribute float aSize;
        uniform float uScale;
        varying vec3 vColor;
        varying float vAlpha;
        void main() {
          vec4 mv = modelViewMatrix * vec4(position, 1.0);
          gl_Position = projectionMatrix * mv;
          float p = clamp(aProgress, 0.0, 1.0);
          // wächst kurz an, schrumpft zum Ende
          float grow = smoothstep(0.0, 0.12, p) * (1.0 - 0.55 * p);
          gl_PointSize = aSize * grow * uScale / max(0.1, -mv.z);
          vColor = mix(aColor0, aColor1, smoothstep(0.0, 0.85, p));
          vAlpha = p >= 1.0 ? 0.0 : pow(1.0 - p, 1.4) * smoothstep(0.0, 0.06, p);
        }
      `,
      fragmentShader: /* glsl */ `
        varying vec3 vColor;
        varying float vAlpha;
        void main() {
          float d = length(gl_PointCoord - 0.5) * 2.0;
          if (d > 1.0) discard;
          float core = pow(1.0 - d, 2.2);
          // heißer, fast weißer Kern
          vec3 c = mix(vColor, vec3(1.0), core * 0.45);
          gl_FragColor = vec4(c * vAlpha * core * 1.6, 1.0);
        }
      `,
      transparent: true,
      depthWrite: false,
      blending: AdditiveBlending,
    })
    this.object = new Points(this.geo, this.material)
    this.object.frustumCulled = false
    this.object.renderOrder = 6
  }

  emit(e: Emit) {
    const i = this.next
    this.next = (this.next + 1) % this.max
    const i3 = i * 3
    this.pos[i3] = e.x
    this.pos[i3 + 1] = e.y
    this.pos[i3 + 2] = e.z
    this.vel[i3] = e.vx
    this.vel[i3 + 1] = e.vy
    this.vel[i3 + 2] = e.vz
    this.c0.set(e.c0, i3)
    this.c1.set(e.c1, i3)
    this.size[i] = e.size
    this.life[i] = e.life
    this.age[i] = 0
    this.prog[i] = 0
    this.lift[i] = e.lift ?? 0
    this.drag[i] = e.drag ?? 0
    this.swirl[i] = e.swirl ?? 0
    this.center[i * 2] = e.cx ?? e.x
    this.center[i * 2 + 1] = e.cz ?? e.z
  }

  update(dt: number) {
    for (let i = 0; i < this.max; i++) {
      if (this.age[i]! >= this.life[i]!) {
        this.prog[i] = 1
        continue
      }
      this.age[i] = this.age[i]! + dt
      this.prog[i] = this.age[i]! / this.life[i]!
      const i3 = i * 3
      const k = Math.exp(-this.drag[i]! * dt)
      this.vel[i3] = this.vel[i3]! * k
      this.vel[i3 + 1] = this.vel[i3 + 1]! * k + this.lift[i]! * dt
      this.vel[i3 + 2] = this.vel[i3 + 2]! * k
      let x = this.pos[i3]! + this.vel[i3]! * dt
      let z = this.pos[i3 + 2]! + this.vel[i3 + 2]! * dt
      const w = this.swirl[i]!
      if (w !== 0) {
        const cx = this.center[i * 2]!
        const cz = this.center[i * 2 + 1]!
        const a = w * dt
        const dx = x - cx
        const dz = z - cz
        x = cx + dx * Math.cos(a) - dz * Math.sin(a)
        z = cz + dx * Math.sin(a) + dz * Math.cos(a)
      }
      this.pos[i3] = x
      this.pos[i3 + 1] = this.pos[i3 + 1]! + this.vel[i3 + 1]! * dt
      this.pos[i3 + 2] = z
    }
    this.geo.attributes.position!.needsUpdate = true
    this.geo.attributes.aProgress!.needsUpdate = true
    this.geo.attributes.aColor0!.needsUpdate = true
    this.geo.attributes.aColor1!.needsUpdate = true
    this.geo.attributes.aSize!.needsUpdate = true
  }

  clear() {
    this.age.set(this.life)
    this.prog.fill(1)
    this.geo.attributes.aProgress!.needsUpdate = true
  }

  dispose() {
    this.geo.dispose()
    this.material.dispose()
  }
}

// ---------------------------------------------------------------- Blitze

/** Gezackte, dicke Blitze um einen Punkt, alle paar Frames neu verästelt. */
class Lightning {
  readonly object = new Group()
  private readonly core: LineSegments2
  private readonly glow: LineSegments2
  private readonly coreMat: LineMaterial
  private readonly glowMat: LineMaterial
  private readonly coreGeo = new LineSegmentsGeometry()
  private readonly glowGeo = new LineSegmentsGeometry()
  private nextBolt = 0

  constructor() {
    this.coreMat = new LineMaterial({
      color: 0xffffff,
      linewidth: 2.5,
      transparent: true,
      depthWrite: false,
      blending: AdditiveBlending,
    })
    this.glowMat = new LineMaterial({
      color: 0xffc23c,
      linewidth: 9,
      transparent: true,
      opacity: 0.35,
      depthWrite: false,
      blending: AdditiveBlending,
    })
    this.core = new LineSegments2(this.coreGeo, this.coreMat)
    this.glow = new LineSegments2(this.glowGeo, this.glowMat)
    for (const l of [this.glow, this.core]) {
      l.frustumCulled = false
      l.renderOrder = 7
      this.object.add(l)
    }
    this.object.visible = false
  }

  setResolution(w: number, h: number) {
    this.coreMat.resolution.set(w, h)
    this.glowMat.resolution.set(w, h)
  }

  /** Neue Blitze um (x, y, z); `strength` 0–1 steuert Anzahl und Reichweite. */
  strike(x: number, y: number, z: number, strength: number, color: string, tSec: number) {
    if (tSec < this.nextBolt) return
    this.nextBolt = tSec + 0.045 + Math.random() * 0.04
    const pts: number[] = []
    const bolts = 3 + Math.round(4 * strength)
    for (let b = 0; b < bolts; b++) {
      const a0 = Math.random() * Math.PI * 2
      // vom Rand des Kreisels nach außen oder als Bogen um ihn herum
      const arc = Math.random() < 0.5
      const a1 = arc ? a0 + (0.8 + Math.random() * 1.6) * (Math.random() < 0.5 ? -1 : 1) : a0
      const r0 = R * (0.75 + Math.random() * 0.2)
      const r1 = arc ? R * (0.8 + Math.random() * 0.3) : R * (1.4 + Math.random() * 1.1 * strength)
      const y0 = y + 0.25 + Math.random() * 0.6
      const y1 = y + (arc ? 0.1 + Math.random() * 0.6 : -0.05 + Math.random() * 0.9)
      const steps = 7
      let px = x + Math.cos(a0) * r0
      let py = y0
      let pz = z + Math.sin(a0) * r0
      for (let s = 1; s <= steps; s++) {
        const u = s / steps
        const a = a0 + (a1 - a0) * u
        const r = r0 + (r1 - r0) * u
        const jitter = s === steps ? 0 : 0.16 * (1 - Math.abs(0.5 - u))
        const nx = x + Math.cos(a) * r + (Math.random() - 0.5) * jitter * 2
        const ny = y0 + (y1 - y0) * u + (Math.random() - 0.5) * jitter * 2
        const nz = z + Math.sin(a) * r + (Math.random() - 0.5) * jitter * 2
        pts.push(px, py, pz, nx, ny, nz)
        // Verästelung
        if (s === 3 && Math.random() < 0.6) {
          pts.push(
            nx,
            ny,
            nz,
            nx + (Math.random() - 0.5) * 0.5,
            ny + (Math.random() - 0.3) * 0.4,
            nz + (Math.random() - 0.5) * 0.5,
          )
        }
        px = nx
        py = ny
        pz = nz
      }
    }
    this.coreGeo.setPositions(pts)
    this.glowGeo.setPositions(pts)
    this.glowMat.color.set(color)
    this.object.visible = true
  }

  /** Flackern: Deckkraft fällt zwischen den Neuverästelungen ab. */
  fade(tSec: number) {
    const left = Math.max(0, this.nextBolt - tSec)
    const k = 0.4 + 0.6 * Math.min(1, left / 0.06)
    this.coreMat.opacity = k
    this.glowMat.opacity = 0.35 * k
  }

  hide() {
    this.object.visible = false
  }

  dispose() {
    this.coreGeo.dispose()
    this.glowGeo.dispose()
    this.coreMat.dispose()
    this.glowMat.dispose()
  }
}

// ---------------------------------------------------------------- Energie-Kuppel

function makeShield() {
  const material = new ShaderMaterial({
    uniforms: {
      uColor: { value: new Color('#5ab4ff') },
      uTime: { value: 0 },
      uOpacity: { value: 0 },
      uHit: { value: 0 },
      uHitDir: { value: new Vector3(1, 0, 0) },
    },
    vertexShader: /* glsl */ `
      varying vec3 vNormal;
      varying vec3 vView;
      varying vec3 vLocal;
      void main() {
        vLocal = normalize(position);
        vec4 wp = modelMatrix * vec4(position, 1.0);
        vNormal = normalize(mat3(modelMatrix) * normal);
        vView = normalize(cameraPosition - wp.xyz);
        gl_Position = projectionMatrix * viewMatrix * wp;
      }
    `,
    fragmentShader: /* glsl */ `
      uniform vec3 uColor;
      uniform float uTime;
      uniform float uOpacity;
      uniform float uHit;
      uniform vec3 uHitDir;
      varying vec3 vNormal;
      varying vec3 vView;
      varying vec3 vLocal;
      // Abstand zur nächsten Hex-Kante in einem Hex-Gitter
      float hexEdge(vec2 p) {
        p *= vec2(1.0, 1.1547);
        p.x += mod(floor(p.y), 2.0) * 0.5;
        vec2 f = abs(fract(p) - 0.5);
        return abs(max(f.x * 1.5 + f.y, f.y * 2.0) - 1.0);
      }
      void main() {
        float fres = pow(1.0 - abs(dot(normalize(vNormal), normalize(vView))), 2.2);
        vec2 uv = vec2(atan(vLocal.z, vLocal.x) * 3.0, vLocal.y * 6.0 - uTime * 0.6);
        float hex = 1.0 - smoothstep(0.0, 0.08, hexEdge(uv * 1.4));
        float scan = 0.5 + 0.5 * sin(vLocal.y * 30.0 - uTime * 6.0);
        // Welle vom Treffpunkt aus
        float dist = acos(clamp(dot(vLocal, normalize(uHitDir)), -1.0, 1.0));
        float ring = uHit * smoothstep(0.35, 0.0, abs(dist - (1.0 - uHit) * 2.2));
        // unten zum Boden hin ausblenden, damit die Kuppel nie sichtbar in der Bowl steckt
        // Bodenhöhe liegt bei etwa -0.65 (Mitte auf halber Kreiselhöhe): erst knapp darüber einblenden
        float base = smoothstep(-0.78, -0.42, vLocal.y);
        float a = (0.12 + 0.85 * fres + 0.35 * hex * (0.4 + fres) + 0.08 * scan + ring * 1.4) * uOpacity * base;
        vec3 c = uColor * (1.0 + fres * 1.5 + ring * 2.0) + vec3(ring * 0.6);
        gl_FragColor = vec4(c * a, a);
      }
    `,
    transparent: true,
    depthWrite: false,
    blending: AdditiveBlending,
  })
  const mesh = new Mesh(new SphereGeometry(1, 48, 32), material)
  mesh.renderOrder = 5
  mesh.visible = false
  return { mesh, material }
}

// ---------------------------------------------------------------- Wirbelsturm

/** Trichter aus zwei gegenläufig drehenden Spiralbändern, unten eng, oben weit. */
function makeTornado() {
  const material = new ShaderMaterial({
    uniforms: {
      uTime: { value: 0 },
      uOpacity: { value: 0 },
      uColor: { value: new Color('#4fe39a') },
    },
    vertexShader: /* glsl */ `
      varying vec2 vUv;
      void main() {
        vUv = uv;
        gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
      }
    `,
    fragmentShader: /* glsl */ `
      uniform float uTime;
      uniform float uOpacity;
      uniform vec3 uColor;
      varying vec2 vUv;
      float band(float x) {
        float f = fract(x);
        return smoothstep(0.0, 0.12, f) * smoothstep(0.42, 0.18, f);
      }
      void main() {
        float y = vUv.y;
        float a1 = band(vUv.x * 3.0 + y * 1.6 - uTime * 2.2);
        float a2 = band(vUv.x * 5.0 - y * 2.2 - uTime * 3.1) * 0.6;
        // unten und oben weich auslaufen
        float edge = smoothstep(0.0, 0.18, y) * pow(1.0 - y, 0.8);
        float a = (a1 + a2) * edge * uOpacity;
        vec3 c = mix(uColor, vec3(0.85, 1.0, 0.9), a1 * 0.6);
        gl_FragColor = vec4(c * a, a);
      }
    `,
    transparent: true,
    depthWrite: false,
    blending: AdditiveBlending,
    side: DoubleSide,
  })
  const geo = new CylinderGeometry(1, 0.35, 1, 48, 8, true)
  geo.translate(0, 0.5, 0)
  const mesh = new Mesh(geo, material)
  mesh.renderOrder = 5
  mesh.visible = false
  return { mesh, material }
}

// ---------------------------------------------------------------- Zusammenspiel

/**
 * Leuchten einer Ult. Bewusst kein eigenes PointLight: Jedes Licht kostet in jedem Pixel jedes metallischen
 * Materials Rechenzeit. Die Szene gibt das hellste Leuchten an ihr vorhandenes Blitzlicht weiter.
 */
class Glow {
  readonly position = new Vector3()
  readonly color = new Color()
  intensity = 0
}

const FIRE_HOT: RGB = [1, 0.92, 0.55]
const FIRE_DEEP: RGB = [0.95, 0.18, 0.02]
const EMBER: RGB = [1, 0.55, 0.15]
const LIGHTNING_CORE: RGB = [1, 1, 0.85]
const SHIELD_BLUE: RGB = [0.45, 0.75, 1]
const WIND_HOT: RGB = [0.75, 1, 0.85]
const WIND_DEEP: RGB = [0.1, 0.75, 0.45]

function hexRgb(hex: string): RGB {
  const n = parseInt(hex.slice(1), 16)
  return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255]
}

const rand = (a: number, b: number) => a + Math.random() * (b - a)

interface SideFx {
  /** Leuchten der Ult (kein eigenes Licht, siehe {@link UltFx.glow}) */
  light: Glow
  lightning: Lightning
  shield: ReturnType<typeof makeShield>
  tornado: ReturnType<typeof makeTornado>
  /** nächster Heil-Puls des Zweiten Winds (s) */
  nextPulse: number
  /** Rest-Emissionen als Bruchteile */
  carry: number
  shieldHit: number
}

export class UltFx {
  readonly object = new Group()
  private readonly particles = new ParticleField(2600)
  private readonly sides: Record<Side, SideFx>
  private lastTriggered = new Set<string>()

  constructor(private readonly calm: boolean) {
    this.object.add(this.particles.object)
    const make = (): SideFx => {
      const light = new Glow()
      const lightning = new Lightning()
      const shield = makeShield()
      const tornado = makeTornado()
      this.object.add(lightning.object, shield.mesh, tornado.mesh)
      return { light, lightning, shield, tornado, carry: 0, shieldHit: 0, nextPulse: 0 }
    }
    this.sides = { A: make(), B: make() }
  }

  /** Hellstes Ult-Leuchten gerade, für das Licht der Szene; null, wenn keins. */
  glow(): Glow | null {
    const a = this.sides.A.light
    const b = this.sides.B.light
    const best = a.intensity >= b.intensity ? a : b
    return best.intensity > 0.05 ? best : null
  }

  /** Bildgröße für Punktgrößen und Linienbreiten. */
  setViewport(width: number, height: number) {
    this.particles.material.uniforms.uScale!.value = height * 0.5
    for (const s of Object.values(this.sides)) s.lightning.setResolution(width, height)
  }

  /** Schild getroffen (Abpraller): Welle von dieser Richtung. */
  shieldHit(side: Side, fromX: number, fromZ: number, pose: FighterPose) {
    const fx = this.sides[side]
    fx.shieldHit = 1
    const u = fx.shield.material.uniforms
    ;(u.uHitDir!.value as Vector3).set(fromX - pose.x, 0.35, fromZ - pose.z).normalize()
  }

  /** Explosion beim Auslösen einer Ult. */
  burst(pose: FighterPose, ult: UltType, power: number, color: string) {
    const c = hexRgb(color)
    const n = this.calm ? 40 : Math.round(140 * power)
    for (let i = 0; i < n; i++) {
      const a = Math.random() * Math.PI * 2
      const sp = rand(3, 8) * power
      this.particles.emit({
        x: pose.x + Math.cos(a) * R * 0.6,
        y: pose.y + rand(0.2, 0.5),
        z: pose.z + Math.sin(a) * R * 0.6,
        vx: Math.cos(a) * sp,
        vy: rand(0.5, 3.5),
        vz: Math.sin(a) * sp,
        life: rand(0.45, 0.9),
        size: rand(0.18, 0.35),
        c0: [1, 1, 1],
        c1: ult === 'SMASH' ? FIRE_DEEP : c,
        drag: 3,
      })
    }
    // Säule nach oben
    for (let i = 0; i < (this.calm ? 15 : 60); i++) {
      this.particles.emit({
        x: pose.x + rand(-0.2, 0.2),
        y: pose.y + 0.3,
        z: pose.z + rand(-0.2, 0.2),
        vx: rand(-0.4, 0.4),
        vy: rand(5, 11),
        vz: rand(-0.4, 0.4),
        life: rand(0.4, 0.7),
        size: rand(0.3, 0.55),
        c0: [1, 1, 1],
        c1: c,
        drag: 2.5,
      })
    }
  }

  /** Glutkern am Treffpunkt: weißglühende Funken mit Schwerkraft, je härter desto mehr. */
  hitFlash(x: number, y: number, z: number, intensity: number) {
    const n = Math.round((this.calm ? 10 : 46) * (0.4 + intensity))
    for (let i = 0; i < n; i++) {
      const a = Math.random() * Math.PI * 2
      const sp = rand(2, 7) * (0.5 + intensity)
      this.particles.emit({
        x,
        y,
        z,
        vx: Math.cos(a) * sp,
        vy: rand(1, 5),
        vz: Math.sin(a) * sp,
        life: rand(0.25, 0.6),
        size: rand(0.06, 0.13),
        c0: [1, 1, 0.9],
        c1: [1, 0.45, 0.1],
        lift: -14,
        drag: 1.2,
      })
    }
    // kurzes, helles Aufblitzen genau am Kontakt
    this.particles.emit({
      x,
      y,
      z,
      vx: 0,
      vy: 0,
      vz: 0,
      life: 0.12,
      size: 0.6 + 0.9 * intensity,
      c0: [1, 1, 1],
      c1: [1, 0.8, 0.5],
    })
  }

  /** Großer Feuerstoß beim Treffer des Vernichtungsschlags. */
  smashImpact(x: number, y: number, z: number) {
    for (let i = 0; i < (this.calm ? 50 : 220); i++) {
      const a = Math.random() * Math.PI * 2
      const sp = rand(2, 9)
      this.particles.emit({
        x,
        y: y + rand(0, 0.3),
        z,
        vx: Math.cos(a) * sp,
        vy: rand(0.5, 5),
        vz: Math.sin(a) * sp,
        life: rand(0.5, 1.1),
        size: rand(0.25, 0.6),
        c0: FIRE_HOT,
        c1: FIRE_DEEP,
        drag: 2.2,
        lift: 2.5,
      })
    }
  }

  /**
   * Pro Frame: laufende Ults emittieren, Lichter, Blitze, Kuppel.
   * @param t Kampfzeit (ms), dt Sekunden
   */
  update(
    t: number,
    dt: number,
    poses: Record<Side, FighterPose>,
    windows: readonly UltWindow[],
    solo: boolean,
  ) {
    const tSec = t / 1000
    for (const side of solo ? (['A'] as const) : (['A', 'B'] as const)) {
      const fx = this.sides[side]
      const pose = poses[side]
      // Feuer brennt beim Vernichtungsschlag noch nach dem Treffer weiter
      const w = pose.burst
        ? null
        : (windows.find(
            (x) => x.side === side && t >= x.start && t < x.end + (x.ult === 'SMASH' ? 1600 : 0),
          ) ?? null)
      fx.shieldHit = Math.max(0, fx.shieldHit - dt * 2.2)
      if (!w) {
        fx.light.intensity *= Math.exp(-dt / 0.12)
        fx.lightning.hide()
        fx.shield.mesh.visible = false
        fx.tornado.mesh.visible = false
        continue
      }
      const age = (t - w.start) / 1000
      const fadeIn = Math.min(1, age / 0.15)
      const fadeOut =
        w.ult === 'SMASH' ? Math.min(1, (w.end + 1600 - t) / 600) : Math.min(1, (w.end - t) / 250)
      const k = fadeIn * Math.max(0, fadeOut)
      const rate = (this.calm ? 0.3 : 1) * k
      fx.light.position.set(pose.x, pose.y + 0.6, pose.z)
      switch (w.ult) {
        case 'SMASH':
          this.fire(fx, pose, dt, rate * w.power, t < w.end)
          fx.light.color.set('#ff6a1a')
          fx.light.intensity = (14 + 8 * Math.sin(tSec * 31) * Math.sin(tSec * 17)) * k * w.power
          break
        case 'RUSH':
          this.rush(fx, pose, dt, rate * w.power, tSec)
          fx.light.color.set('#ffd25a')
          fx.light.intensity = (Math.random() < 0.3 ? 22 : 8) * k
          break
        case 'SHIELD':
          this.shield(fx, pose, dt, rate, tSec, k)
          fx.light.color.set('#5ab4ff')
          fx.light.intensity = (7 + 10 * fx.shieldHit) * k
          break
        case 'SURGE':
          this.surge(fx, pose, dt, rate * w.power, tSec, k)
          fx.light.color.set('#4fe39a')
          fx.light.intensity = (8 + 3 * Math.sin(tSec * 9)) * k
          break
      }
      if (w.ult !== 'RUSH') fx.lightning.hide()
      if (w.ult !== 'SHIELD') fx.shield.mesh.visible = false
      if (w.ult !== 'SURGE') fx.tornado.mesh.visible = false
    }
    this.particles.update(dt)
  }

  private count(fx: SideFx, perSecond: number, dt: number) {
    fx.carry += perSecond * dt
    const n = Math.floor(fx.carry)
    fx.carry -= n
    return n
  }

  /** Flammen hüllen den Kreisel ein, Glut fliegt; beim Ausholen wird Energie eingesogen. */
  private fire(fx: SideFx, pose: FighterPose, dt: number, rate: number, charging: boolean) {
    for (let i = this.count(fx, 380 * rate, dt); i > 0; i--) {
      const a = Math.random() * Math.PI * 2
      const r = R * rand(0.25, 0.95)
      this.particles.emit({
        x: pose.x + Math.cos(a) * r,
        y: pose.y + rand(0.3, 0.85),
        z: pose.z + Math.sin(a) * r,
        // tangential mitreißen, als ob die Flammen vom Spin herumgeschleudert werden
        vx: -Math.sin(a) * 1.4 + rand(-0.3, 0.3),
        vy: rand(1.2, 2.8),
        vz: Math.cos(a) * 1.4 + rand(-0.3, 0.3),
        life: rand(0.35, 0.75),
        size: rand(0.35, 0.75),
        c0: FIRE_HOT,
        c1: FIRE_DEEP,
        lift: 3,
        drag: 1.5,
      })
    }
    if (Math.random() < 50 * rate * dt) {
      const a = Math.random() * Math.PI * 2
      this.particles.emit({
        x: pose.x + Math.cos(a) * R * 0.6,
        y: pose.y + 0.7,
        z: pose.z + Math.sin(a) * R * 0.6,
        vx: Math.cos(a) * rand(1, 3),
        vy: rand(2, 5),
        vz: Math.sin(a) * rand(1, 3),
        life: rand(0.8, 1.5),
        size: rand(0.06, 0.12),
        c0: [1, 0.95, 0.7],
        c1: EMBER,
        lift: -2,
        drag: 0.8,
      })
    }
    if (charging) {
      for (let i = 0; i < Math.round(6 * rate); i++) {
        const a = Math.random() * Math.PI * 2
        const r = R * rand(2.2, 3)
        const life = rand(0.3, 0.45)
        this.particles.emit({
          x: pose.x + Math.cos(a) * r,
          y: pose.y + rand(0.2, 1.2),
          z: pose.z + Math.sin(a) * r,
          vx: (-Math.cos(a) * r) / life,
          vy: 0,
          vz: (-Math.sin(a) * r) / life,
          life,
          size: rand(0.1, 0.2),
          c0: [1, 0.5, 0.3],
          c1: [1, 0.95, 0.8],
        })
      }
    }
  }

  /** Blitze zucken um den Kreisel, Funken sprühen, leuchtende Spur. */
  private rush(fx: SideFx, pose: FighterPose, dt: number, rate: number, tSec: number) {
    fx.lightning.strike(pose.x, pose.y, pose.z, Math.min(1, rate), '#ffc23c', tSec)
    fx.lightning.fade(tSec)
    for (let i = this.count(fx, 160 * rate, dt); i > 0; i--) {
      const a = Math.random() * Math.PI * 2
      this.particles.emit({
        x: pose.x + Math.cos(a) * R * 0.8,
        y: pose.y + rand(0.25, 0.8),
        z: pose.z + Math.sin(a) * R * 0.8,
        vx: Math.cos(a) * rand(1, 4),
        vy: rand(0.5, 2.5),
        vz: Math.sin(a) * rand(1, 4),
        life: rand(0.15, 0.35),
        size: rand(0.08, 0.16),
        c0: LIGHTNING_CORE,
        c1: [1, 0.7, 0.1],
        lift: -6,
        drag: 2,
      })
    }
    // Nachleuchten an der Stelle, an der der Kreisel gerade war
    if (Math.random() < 40 * rate * dt) {
      this.particles.emit({
        x: pose.x,
        y: pose.y + 0.3,
        z: pose.z,
        vx: 0,
        vy: 0.2,
        vz: 0,
        life: 0.35,
        size: BEY_SIZE * 0.9,
        c0: [1, 0.85, 0.3],
        c1: [0.6, 0.35, 0.05],
      })
    }
  }

  /** Energie-Kuppel mit Hex-Muster, Wellen bei Treffern, Lichtpunkte kreisen. */
  private shield(fx: SideFx, pose: FighterPose, dt: number, rate: number, tSec: number, k: number) {
    const s = fx.shield
    s.mesh.visible = true
    const bump = 1 + 0.12 * fx.shieldHit
    // Blase um den ganzen Kreisel (gut 1 Einheit hoch, 1,45 breit), Mitte auf halber Höhe, mit Luft fürs
    // Kippen; der Teil unter dem Boden blendet im Shader aus
    s.mesh.scale.set(R * 1.42 * bump, BEY_HEIGHT * 0.82 * bump, R * 1.42 * bump)
    s.mesh.position.set(pose.x, pose.y + BEY_HEIGHT * 0.52, pose.z)
    const u = s.material.uniforms
    u.uTime!.value = tSec
    u.uOpacity!.value = k
    u.uHit!.value = fx.shieldHit
    for (let i = this.count(fx, 70 * rate, dt); i > 0; i--) {
      const a = Math.random() * Math.PI * 2
      const r = R * 1.5
      this.particles.emit({
        x: pose.x + Math.cos(a) * r,
        y: pose.y + rand(0.05, 0.9),
        z: pose.z + Math.sin(a) * r,
        vx: 0,
        vy: rand(0.2, 0.8),
        vz: 0,
        life: rand(0.6, 1.1),
        size: rand(0.08, 0.15),
        c0: [0.9, 0.97, 1],
        c1: SHIELD_BLUE,
        swirl: 2.5,
        cx: pose.x,
        cz: pose.z,
      })
    }
  }

  /**
   * Zweiter Wind: Wirbelsturm-Trichter um den Kreisel, aufsteigende Ströme, die ihm folgen, und
   * regelmäßige Heil-Pulse, die als grüner Ring nach außen und oben gehen.
   */
  private surge(fx: SideFx, pose: FighterPose, dt: number, rate: number, tSec: number, k: number) {
    const t = fx.tornado
    t.mesh.visible = true
    const breathe = 1 + 0.06 * Math.sin(tSec * 7)
    t.mesh.position.set(pose.x, pose.y, pose.z)
    t.mesh.scale.set(R * 1.7 * breathe, 2.2 * Math.min(1, k * 1.4), R * 1.7 * breathe)
    t.mesh.rotation.y = tSec * 5
    t.material.uniforms.uTime!.value = tSec
    t.material.uniforms.uOpacity!.value = 0.9 * k

    // Ströme: kurzlebig, damit sie dem fahrenden Kreisel folgen
    for (let i = this.count(fx, 320 * rate, dt); i > 0; i--) {
      const a = Math.random() * Math.PI * 2
      const r = R * rand(0.5, 1.2)
      this.particles.emit({
        x: pose.x + Math.cos(a) * r,
        y: pose.y + rand(0, 0.3),
        z: pose.z + Math.sin(a) * r,
        vx: Math.cos(a) * 0.5,
        vy: rand(2.5, 4.5),
        vz: Math.sin(a) * 0.5,
        life: rand(0.35, 0.6),
        size: rand(0.14, 0.3),
        c0: WIND_HOT,
        c1: WIND_DEEP,
        swirl: rand(7, 10),
        cx: pose.x,
        cz: pose.z,
        drag: 0.6,
      })
    }

    // Heil-Puls: Ring aus Lichtpunkten, der sich ausbreitet und aufsteigt
    if (tSec >= fx.nextPulse) {
      fx.nextPulse = tSec + 0.7
      const n = this.calm ? 16 : 48
      for (let i = 0; i < n; i++) {
        const a = (i / n) * Math.PI * 2
        this.particles.emit({
          x: pose.x + Math.cos(a) * R * 0.5,
          y: pose.y + 0.15,
          z: pose.z + Math.sin(a) * R * 0.5,
          vx: Math.cos(a) * 3.2,
          vy: 1.6,
          vz: Math.sin(a) * 3.2,
          life: 0.6,
          size: 0.22,
          c0: [0.9, 1, 0.95],
          c1: WIND_DEEP,
          drag: 2.5,
        })
      }
    }
  }

  reset() {
    this.particles.clear()
    this.lastTriggered.clear()
    for (const s of Object.values(this.sides)) {
      s.light.intensity = 0
      s.lightning.hide()
      s.shield.mesh.visible = false
      s.tornado.mesh.visible = false
      s.nextPulse = 0
    }
  }

  dispose() {
    this.particles.dispose()
    for (const s of Object.values(this.sides)) {
      s.lightning.dispose()
      s.shield.mesh.geometry.dispose()
      s.shield.material.dispose()
      s.tornado.mesh.geometry.dispose()
      s.tornado.material.dispose()
    }
  }
}
