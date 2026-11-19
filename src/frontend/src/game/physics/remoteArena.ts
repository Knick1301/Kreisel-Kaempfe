import type { ArenaRoomDto, FinishType, Side, UltType } from '@/api'
import { auth } from '@/auth'

/** Stand eines Kreisels vom Server */
export interface BodyState {
  x: number
  z: number
  vx: number
  vz: number
  /** Spin 0–1 */
  spin: number
  /** Ult-Leiste 0–1 */
  meter: number
  /** gerade laufende Ult */
  ult: UltType | null
  ultReady: boolean
  /** Dash wieder bereit, 0–1 */
  dash: number
  hits: number
  dashes: number
  out: boolean
}

/** Ereignis aus der Server-Physik (Effekte, Sound, Treffer-Anzeigen) */
export interface ArenaEvent {
  type: 'CLASH' | 'DASH' | 'ULT' | 'ULT_END' | 'WALL' | 'FINISH'
  t: number
  /** CLASH: Angreifer, FINISH: Sieger, sonst die betroffene Seite */
  side: Side
  intensity: number
  x: number
  z: number
  nx: number
  nz: number
  deflected: boolean
  ult: UltType | null
  finish: FinishType | null
}

export type ArenaStatus = 'LAUNCH' | 'COUNTDOWN' | 'FIGHT' | 'DONE'

export interface StateMessage {
  type: 'state'
  status: ArenaStatus
  /** Kampfzeit in ms */
  t: number
  countdownMs: number
  /** Ult-Einblendung: Kampf steht */
  frozen: boolean
  a?: BodyState
  b?: BodyState
  events: ArenaEvent[]
}

export interface RoomMessage {
  type: 'room'
  room: ArenaRoomDto
}

/** Was die 3D-Darstellung braucht (siehe `LiveChoreography`) */
export interface ArenaBodies {
  readonly bodies: Record<Side, BodyState>
}

interface Snapshot {
  t: number
  /** lokale Ankunftszeit (performance.now) */
  at: number
  a: BodyState
  b: BodyState
}

/** So weit hinter dem neuesten Stand zeigen wir an, damit zwischen zwei Ständen interpoliert werden kann. */
export const RENDER_DELAY_MS = 60
/** Höchstens so weit über den letzten Stand hinaus schätzen (Ruckler im Netz) */
const MAX_AHEAD_MS = 100

const emptyBody = (): BodyState => ({
  x: 0,
  z: 0,
  vx: 0,
  vz: 0,
  spin: 1,
  meter: 0,
  ult: null,
  ultReady: false,
  dash: 1,
  hits: 0,
  dashes: 0,
  out: false,
})

/**
 * Verbindung zu einem Arena-Raum auf dem Server. Der Server rechnet die Physik, wir schicken nur Eingaben und
 * zeigen den Stand an, leicht verzögert und zwischen zwei Ständen weich interpoliert.
 */
export class RemoteArena implements ArenaBodies {
  readonly bodies: Record<Side, BodyState> = { A: emptyBody(), B: emptyBody() }
  /** neuester Stand vom Server (für HUD, Status, Countdown) */
  latest: StateMessage | null = null
  onEvents: (events: ArenaEvent[]) => void = () => {}
  onRoom: (room: ArenaRoomDto) => void = () => {}
  onClose: () => void = () => {}

  private socket: WebSocket | null = null
  private snaps: Snapshot[] = []
  private lastInput = ''
  private closed = false

  constructor(readonly roomId: string) {}

  connect(url = RemoteArena.url(this.roomId)) {
    this.closed = false
    const ws = new WebSocket(url)
    this.socket = ws
    ws.onmessage = (e) => this.receive(String(e.data))
    ws.onclose = () => {
      if (this.socket === ws) this.socket = null
      if (!this.closed) this.onClose()
    }
  }

  static url(roomId: string) {
    const loc = globalThis.location
    const proto = loc?.protocol === 'https:' ? 'wss:' : 'ws:'
    const q = `room=${encodeURIComponent(roomId)}&token=${encodeURIComponent(auth.token ?? '')}`
    return `${proto}//${loc?.host ?? 'localhost'}/ws/arena?${q}`
  }

  get connected() {
    return this.socket?.readyState === 1
  }

  close() {
    this.closed = true
    this.socket?.close()
    this.socket = null
  }

  /**
   * Eingabe schicken. Die Richtung nur, wenn sie sich geändert hat; Dash und Ult als einmaliger Druck.
   */
  send(x: number, z: number, dash: boolean, ult: boolean) {
    const ws = this.socket
    if (!ws || ws.readyState !== 1) return
    const rx = Math.round(x * 100) / 100
    const rz = Math.round(z * 100) / 100
    const key = `${rx},${rz}`
    if (key === this.lastInput && !dash && !ult) return
    this.lastInput = key
    ws.send(JSON.stringify({ type: 'input', x: rx, z: rz, dash, ult }))
  }

  /** Nachricht vom Server verarbeiten (öffentlich für Tests). */
  receive(text: string, now = performance.now()) {
    let msg: StateMessage | RoomMessage
    try {
      msg = JSON.parse(text)
    } catch {
      return
    }
    if (msg.type === 'room') {
      this.onRoom(msg.room)
      return
    }
    if (msg.type !== 'state') return
    this.latest = msg
    if (msg.a && msg.b) {
      this.snaps.push({ t: msg.t, at: now, a: msg.a, b: msg.b })
      // genug für die Interpolation behalten
      while (this.snaps.length > 2 && this.snaps[1]!.t < msg.t - 1000) this.snaps.shift()
      if (this.snaps.length === 1) {
        Object.assign(this.bodies.A, msg.a)
        Object.assign(this.bodies.B, msg.b)
      }
    }
    if (msg.events.length) this.onEvents(msg.events)
  }

  /**
   * Darstellung für jetzt berechnen: geschätzte Server-Zeit minus Verzögerung, Körper dazwischen interpoliert.
   *
   * @returns Kampfzeit in ms, die gerade angezeigt wird
   */
  update(now = performance.now()): number {
    const last = this.snaps[this.snaps.length - 1]
    if (!last) return 0
    const frozen = this.latest?.frozen ?? false
    const ahead = frozen ? 0 : Math.min(MAX_AHEAD_MS, now - last.at)
    const renderT = Math.max(0, last.t + ahead - RENDER_DELAY_MS)
    let i = this.snaps.length - 1
    while (i > 0 && this.snaps[i - 1]!.t > renderT) i--
    const s1 = this.snaps[i]!
    const s0 = this.snaps[Math.max(0, i - 1)]!
    const span = s1.t - s0.t
    const k = span > 0 ? Math.max(0, Math.min(1, (renderT - s0.t) / span)) : 1
    lerpBody(this.bodies.A, s0.a, s1.a, k)
    lerpBody(this.bodies.B, s0.b, s1.b, k)
    return Math.min(renderT, last.t)
  }
}

function lerpBody(out: BodyState, a: BodyState, b: BodyState, k: number) {
  const l = (x: number, y: number) => x + (y - x) * k
  out.x = l(a.x, b.x)
  out.z = l(a.z, b.z)
  out.vx = l(a.vx, b.vx)
  out.vz = l(a.vz, b.vz)
  out.spin = l(a.spin, b.spin)
  out.meter = b.meter
  out.ult = b.ult
  out.ultReady = b.ultReady
  out.dash = b.dash
  out.hits = b.hits
  out.dashes = b.dashes
  out.out = b.out
}
