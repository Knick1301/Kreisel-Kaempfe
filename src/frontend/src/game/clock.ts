/**
 * Wiedergabe-Uhr in Kampfzeit (ms). Läuft per `advance()` mit einem Tempo, das von der aktuellen Zeit
 * abhängen darf (Slow-Mo). Die 3D-Szene, das HUD und der Textverlauf lesen alle dieselbe Uhr.
 */
export class BattleClock {
  t = -1
  playing = false
  private rate: (tMs: number) => number = () => 1

  setRate(rate: (tMs: number) => number) {
    this.rate = rate
  }

  play(fromMs?: number) {
    if (fromMs !== undefined) this.t = fromMs
    this.playing = true
  }

  pause() {
    this.playing = false
  }

  seek(tMs: number) {
    this.t = tMs
  }

  /** Wanduhr-Zeit seit dem letzten Frame, auf 250 ms begrenzt (Tab im Hintergrund, Ruckler). */
  advance(dtMs: number) {
    if (!this.playing) return
    this.t += Math.min(Math.max(dtMs, 0), 250) * this.rate(this.t)
  }
}
