/**
 * Physikalisch angelehnte Klangerzeugung für Metall (Modalsynthese), ohne Aufnahmen.
 *
 * Ein angeschlagenes Blech schwingt in vielen unharmonischen Eigenfrequenzen (Moden), jede klingt
 * unterschiedlich schnell aus. Ein harter Anschlag liefert zusätzlich einen kurzen, breitbandigen Knack. Die
 * Summe, leicht gesättigt, klingt wie ein Schlag auf eine Aluwand. Berechnet wird einmal beim Start, als
 * Float32-Daten, die der Aufrufer in einen AudioBuffer kopiert.
 */

export interface PlateOptions {
  /** tiefste und höchste Mode (Hz) */
  low: number
  high: number
  /** Anzahl Moden: mehr = dichter, „blechiger“ */
  modes: number
  /** Ausklingzeit-Faktor in Sekunden (tiefe Moden klingen länger) */
  decay: number
  /** Lautstärke des Anschlag-Knacks 0–1 */
  crack: number
  /** Sättigung für Krachen (0 = sauber) */
  drive: number
  /** Länge in Sekunden */
  length: number
  /** Startwert für den Zufall, damit Varianten reproduzierbar sind */
  seed: number
}

/** Kleiner deterministischer Zufall (Mulberry32). */
function rng(seed: number) {
  let a = seed >>> 0
  return () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

/** Schlag auf ein Metallblech. */
export function plateHit(sampleRate: number, o: PlateOptions): Float32Array<ArrayBuffer> {
  const n = Math.floor(sampleRate * o.length)
  const out = new Float32Array(n)
  const r = rng(o.seed)

  for (let m = 0; m < o.modes; m++) {
    // logarithmisch verteilt, leicht gehäuft wie bei einer echten Platte
    const f = o.low * Math.pow(o.high / o.low, Math.pow(r(), 0.85))
    const amp = (0.4 + 0.6 * r()) / Math.pow(f / o.low, 0.45)
    const tau = o.decay * (0.25 + 0.9 * r()) * Math.pow(500 / f, 0.35)
    // Schwebung: zwei fast gleiche Moden, typisch für Bleche
    const beat = 1 + (r() - 0.5) * 0.004
    const w1 = (2 * Math.PI * f) / sampleRate
    const w2 = w1 * beat
    const k = Math.exp(-1 / (tau * sampleRate))
    // Rotations-Rekursion statt Math.sin: Zeiger (s, c) pro Sample um w drehen und dämpfen
    const cos1 = Math.cos(w1) * k
    const sin1 = Math.sin(w1) * k
    const cos2 = Math.cos(w2) * k
    const sin2 = Math.sin(w2) * k
    const p1 = r() * Math.PI * 2
    const p2 = r() * Math.PI * 2
    let s1 = amp * Math.sin(p1)
    let c1 = amp * Math.cos(p1)
    let s2 = 0.6 * amp * Math.sin(p2)
    let c2 = 0.6 * amp * Math.cos(p2)
    // nach 6 Zeitkonstanten ist die Mode unhörbar (−52 dB)
    const end = Math.min(n, Math.ceil(tau * sampleRate * 6))
    for (let i = 0; i < end; i++) {
      out[i]! += s1 + s2
      const ns1 = s1 * cos1 + c1 * sin1
      c1 = c1 * cos1 - s1 * sin1
      s1 = ns1
      const ns2 = s2 * cos2 + c2 * sin2
      c2 = c2 * cos2 - s2 * sin2
      s2 = ns2
    }
  }

  // Anschlag: kurzer, heller Knack (differenziertes Rauschen)
  const crackLen = Math.floor(sampleRate * 0.012)
  let prev = 0
  for (let i = 0; i < crackLen; i++) {
    const x = r() * 2 - 1
    const e = Math.exp(-i / (sampleRate * 0.0025))
    out[i]! += o.crack * 6 * (x - prev) * e
    prev = x
  }

  // normalisieren und sättigen
  let peak = 1e-6
  for (let i = 0; i < n; i++) peak = Math.max(peak, Math.abs(out[i]!))
  const g = 1 / peak
  const drive = 1 + o.drive * 4
  const norm = Math.tanh(drive)
  for (let i = 0; i < n; i++) out[i] = (Math.tanh(out[i]! * g * drive) / norm) * 0.95
  // Ende sanft ausblenden
  const fade = Math.floor(sampleRate * 0.05)
  for (let i = 0; i < fade; i++) out[n - 1 - i]! *= i / fade
  return out
}

/**
 * Mechanisches Kreisel-Surren als nahtlose Schleife: Lager- und Zahnklicks im Rotationstakt regen
 * Metallresonanzen an, dazu Schleifen auf der Bowl, das mit der Rotation pulsiert, und ein tiefes Brummen.
 * Abgespielt mit veränderter Geschwindigkeit steigen Tonhöhe und Taktrate gemeinsam, wie bei echtem Spin.
 *
 * @param rotation Umdrehungen pro Sekunde bei Abspielrate 1
 */
export function spinLoop(
  sampleRate: number,
  seconds = 2,
  rotation = 40,
  seed = 7,
): Float32Array<ArrayBuffer> {
  // Länge auf ganze Umdrehungen runden, damit die Schleife nahtlos ist
  const turns = Math.round(seconds * rotation)
  const n = Math.round((turns / rotation) * sampleRate)
  // etwas länger rechnen: das Überstehende wird am Ende in den Anfang geblendet (nahtlose Schleife)
  const blend = Math.floor(sampleRate * 0.02)
  const total = n + blend
  const out = new Float32Array(total)
  const r = rng(seed)
  const period = sampleRate / rotation

  // Resonanzen des Metallrings (gedämpfte Schwinger, von Klicks angeregt)
  const res = [1830, 2870, 4120, 5960, 7400].map((f) => ({
    w: (2 * Math.PI * f) / sampleRate,
    k: Math.exp(-1 / (0.006 * sampleRate)),
    s: 0,
    c: 0,
    g: 0.6 + 0.4 * r(),
  }))
  // Klicks: 6 Zähne/Kanten pro Umdrehung, leicht unregelmäßig
  const teeth = 6
  const clicks: number[] = []
  for (let t = 0; t < turns * teeth; t++) {
    clicks.push(Math.floor((t / teeth) * period + (r() - 0.5) * period * 0.04) % n)
  }
  const clickAt = new Float32Array(total)
  for (const c of clicks) {
    const at = (c + n) % n
    const v = 0.5 + 0.5 * r()
    clickAt[at] = v
    if (at + n < total) clickAt[at + n] = v
  }

  let lp = 0
  for (let i = 0; i < total; i++) {
    const phase = (i / period) * 2 * Math.PI
    // Anregung: Klick plus schleifendes Rauschen, das mit der Rotation pulsiert
    const grind = (r() * 2 - 1) * (0.25 + 0.2 * Math.sin(phase) + 0.1 * Math.sin(phase * 3))
    const exc = clickAt[i]! + grind * 0.35
    let y = 0
    for (const o of res) {
      // gedämpfter Resonator (rotierender Zeiger)
      const s = (o.s * Math.cos(o.w) - o.c * Math.sin(o.w)) * o.k + exc * o.g
      const c = (o.s * Math.sin(o.w) + o.c * Math.cos(o.w)) * o.k
      o.s = s
      o.c = c
      y += s
    }
    // tiefes, raues Brummen im Rotationstakt (Unwucht)
    const hum =
      0.5 * Math.sin(phase) + 0.25 * Math.sin(2 * phase + 0.6) + 0.15 * Math.sin(3 * phase + 1.3)
    // etwas Tiefpass gegen Härte
    lp += (y - lp) * 0.45
    out[i] = lp * 0.18 + hum * 0.35 + grind * 0.08
  }
  // Überstand in den Anfang blenden: out[n-1] → out[0] geht dann glatt weiter
  for (let i = 0; i < blend; i++) {
    const u = i / blend
    out[i] = out[i]! * u + out[n + i]! * (1 - u)
  }
  const loop = out.slice(0, n)
  let peak = 1e-6
  for (let i = 0; i < n; i++) peak = Math.max(peak, Math.abs(loop[i]!))
  for (let i = 0; i < n; i++) loop[i] = (loop[i]! / peak) * 0.9
  return loop
}
