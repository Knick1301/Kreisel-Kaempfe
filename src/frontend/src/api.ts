export interface PartDto {
  id: number
  name: string
  modelPath: string | null
  weight: number
  attack: number
  defense: number
  stamina: number
  dash: number
  burstResistance: number
  /** nur beim Fusion Wheel: dessen Ult */
  ult?: UltType
}

/** Ult eines Kreisels, festgelegt durch sein Fusion Wheel. */
export type UltType = 'SMASH' | 'SHIELD' | 'RUSH' | 'SURGE'

export const ULT_LABEL: Record<UltType, string> = {
  SMASH: 'Vernichtungsschlag',
  SHIELD: 'Schildwall',
  RUSH: 'Ansturm',
  SURGE: 'Zweiter Wind',
}

/** Kurzbeschreibung für Baukasten und Intro. */
export const ULT_HINT: Record<UltType, string> = {
  SMASH: 'Ein nicht abwehrbarer Treffer mit voller Wucht, bricht Schilde.',
  SHIELD: 'Angriffe prallen ab, der Kreisel steht fest und verliert kaum Spin.',
  RUSH: 'Ein paar Sekunden pausenlose, harte Angriffe, kostet eigenen Spin.',
  SURGE: 'Holt Spin zurück und senkt kurz die Reibung.',
}

export interface BeybladeStats {
  attack: number
  defense: number
  stamina: number
  dash: number
  weight: number
  burstResistance: number
}

export interface BeybladeDto {
  id: number
  name: string
  fusionWheel: PartDto
  spinTrack: PartDto
  tip: PartDto
  stats: BeybladeStats
}

import { auth, clearSession } from '@/auth'

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message)
  }
}

let onUnauthorized: () => void = () => {}
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {}
  if (auth.token) headers.Authorization = `Bearer ${auth.token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const res = await fetch(`/api${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (res.status === 401 && auth.token) {
    clearSession()
    onUnauthorized()
  }
  if (!res.ok) {
    let message = `${res.status} ${res.statusText}`
    try {
      const data = (await res.json()) as { message?: string }
      if (data?.message) message = data.message
    } catch {}
    throw new ApiError(message, res.status)
  }
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}

const get = <T>(path: string) => request<T>('GET', path)

export function fetchBeyblades() {
  return get<BeybladeDto[]>('/me/beyblades')
}

export function modelUrl(modelPath: string) {
  return `/models/${modelPath}`
}

export type PartKind = 'fusion-wheels' | 'spin-tracks' | 'tips'

export function fetchParts(kind: PartKind) {
  return get<PartDto[]>(`/parts/${kind}`)
}

export function fetchPreview(fusionWheelId: number, spinTrackId: number, tipId: number) {
  const q = new URLSearchParams({
    fusionWheelId: String(fusionWheelId),
    spinTrackId: String(spinTrackId),
    tipId: String(tipId),
  })
  return get<BeybladeStats>(`/beyblades/preview?${q}`)
}

export interface BeybladeRequest {
  name: string
  fusionWheelId: number
  spinTrackId: number
  tipId: number
}

export function createBeyblade(req: BeybladeRequest) {
  return request<BeybladeDto>('POST', '/me/beyblades', req)
}

export function updateBeyblade(id: number, req: BeybladeRequest) {
  return request<BeybladeDto>('PUT', `/me/beyblades/${id}`, req)
}

export function deleteBeyblade(id: number) {
  return request<void>('DELETE', `/me/beyblades/${id}`)
}

export type FinishType = 'BURST' | 'RING_OUT' | 'SPIN_OUT' | 'DECISION'
export type BattleMode = 'PVP' | 'BOT'
/** A ist der Herausforderer, B der Gegner. */
export type Side = 'A' | 'B'
export type Archetype = 'ATTACK' | 'DEFENSE' | 'STAMINA' | 'BALANCE'

export interface StyleShares {
  attack: number
  defense: number
  stamina: number
}

export interface FighterDto {
  side: Side
  username: string
  bot: boolean
  beyblade: string
  stats: BeybladeStats
  style: StyleShares
  archetype: Archetype
  /** Fusion Wheel, Spin Track, Tip – fehlt bei KI-Gegnern (Platzhalter-Modell). */
  parts?: PartDto[]
  /** Akzentfarbe des Platzhalter-Modells, nur bei KI-Gegnern. */
  accent?: string
  /** Ausgewerteter Start, nur bei Kämpfen mit Anspinnen (nie vor dem Kampf). */
  launch?: LaunchResult
  /** Ult des Kreisels (vom Fusion Wheel) */
  ult?: { type: UltType; label: string }
}

export interface LaunchResult {
  /** 0–1: Summe der drei Zonenwerte geteilt durch drei */
  quality: number
  /** getroffene Zonen (0–3) */
  hits: number
  /** davon im Kern */
  cores: number
  /** Landepunkt im Abschussrahmen: so weit fliegt der Kreisel hinein */
  landingX?: number | null
  landingY?: number | null
  /** alle drei Zonen im Kern (sehr selten) */
  perfect: boolean
  /** keine Zone getroffen */
  missed: boolean
}

export interface MatchupDto {
  /** Stil-Vorteil von A gegenüber B, positiv = A im Vorteil. */
  edge: number
  favored?: Side | null
}

export interface ArenaInfo {
  id: string
  name: string
  /** optische Variante dieses Kampfs */
  variant?: string
  variants?: string[]
}

interface EventAt {
  /** ms seit dem Launch */
  t: number
}

export type TimelineEvent =
  | (EventAt & { type: 'LAUNCH' | 'TORNADO' | 'ENGAGE' | 'TIMEOUT' })
  /** angle: Polarwinkel (rad) des Treffpunkts auf der Bowl */
  /** ult: Treffer gehört zur Ult des Angreifers (SMASH, RUSH) oder prallte am Schild ab (SHIELD) */
  | (EventAt & { type: 'CLASH'; attacker: Side; intensity: number; angle: number; ult?: UltType })
  | (EventAt & { type: 'DEFLECT'; defender: Side })
  | (EventAt & { type: 'WOBBLE' | 'BURST' | 'SPIN_OUT'; victim: Side })
  /** angle: Richtung der Tasche, pocket: Index in der Arena-Definition */
  | (EventAt & { type: 'NEAR_RING_OUT' | 'RING_OUT'; victim: Side; angle: number; pocket: number })
  /** intensity: Stärke der Ult (vom Anspinnen), durationMs: Wirkdauer bei Schild, Ansturm, Zweiter Wind */
  | (EventAt & { type: 'ULT'; side: Side; ult: UltType; intensity: number; durationMs?: number })
  | (EventAt & { type: 'ULT_END'; side: Side; ult: UltType })
  /** Leiste voll, die Ult wartet auf den Spieler */
  | (EventAt & { type: 'ULT_READY'; side: Side; ult: UltType })

export type EventType = TimelineEvent['type']

export interface BattleTimeline {
  sampleMs: number
  /** Spin-Energie 0–1, Sample i gehört zu i · sampleMs */
  energy: Record<Side, number[]>
  hits: Record<Side, number>
  events: TimelineEvent[]
  /** Ult-Leiste 0–1 im selben Raster wie `energy`, fehlt bei Kämpfen ohne Ults */
  ult?: Record<Side, number[]>
}

export interface BattleReport {
  id: number
  winner: string
  loser: string
  winnerBeyblade: string
  loserBeyblade: string
  finish: FinishType
  /** Nur bei Kämpfen aus der alten Rundensimulation sinnvoll. */
  rounds: number
  playedAt: string
  mode?: BattleMode
  seed?: number
  durationMs?: number
  winnerSide?: Side
  arena?: ArenaInfo
  fighters?: [FighterDto, FighterDto]
  matchup?: MatchupDto
  /** Fehlt, wenn sich der Kampf nicht exakt nachspielen lässt. */
  timeline?: BattleTimeline
  /** Liga-Punkte-Änderung, fehlt bei Altbestand und für die KI-Seite */
  ratingDeltaWinner?: number
  ratingDeltaLoser?: number
}

export const FINISH_LABEL: Record<FinishType, string> = {
  BURST: 'Burst',
  RING_OUT: 'Ring-Out',
  SPIN_OUT: 'Ausgedreht',
  DECISION: 'Nach Punkten',
}

export const ARCHETYPE_LABEL: Record<Archetype, string> = {
  ATTACK: 'Angriff',
  DEFENSE: 'Verteidigung',
  STAMINA: 'Ausdauer',
  BALANCE: 'Balance',
}

export function quickMatch(beybladeId: number) {
  return request<BattleReport>('POST', '/battles/quick', { beybladeId })
}

/** Kampf gegen einen KI-Gegner mit Zufallsname, Ergebnis und Timeline kommen vom Server. */
export function botBattle(beybladeId: number, arenaId?: string, variantId?: string) {
  return request<BattleReport>('POST', '/battles/bot', { beybladeId, arenaId, variantId })
}

// ---------------------------------------------------------------- Match mit Anspinnen

/** Vorgaben und Regeln fürs Anspinnen. Gewertet wird nur auf dem Server, der Client zeigt sie an. */
/**
 * Schwungmesser: Ein Zeiger pendelt über einen Bogen, p(t) = ½ − ½·cos(2π·t/periodMs). Drei Zonen, drei Taps,
 * jede Zone zählt einmal. Gewertet wird nur auf dem Server, der Client zeigt dieselben Werte an.
 */
export interface LaunchChallenge {
  windowMs: number
  maxTaps: number
  periodMs: number
  /** Mitten der drei Zonen auf dem Bogen (0 = links, 1 = rechts) */
  zones: number[]
  zoneHalf: number
  coreHalf: number
  edgeScore: number
  perfectQuality: number
}

export type MatchStatus = 'LAUNCH' | 'FIGHT' | 'DONE'

export interface MatchDto {
  id: string
  mode: BattleMode
  status: MatchStatus
  yourSide: Side
  arena: ArenaInfo
  fighters: [FighterDto, FighterDto]
  matchup: MatchupDto
  launch: LaunchChallenge
  youLaunched: boolean
  opponentReady: boolean
  expiresInMs: number
  battleId?: number
  /** Kampfbericht, sobald beide angespinnt haben */
  report?: BattleReport
}

/** Rohe Eingabe: Moment des Loslassens (ms ab Start des Zeigers auf dem eigenen Gerät) plus Zug. */
export interface LaunchInput {
  taps: number[]
  /** Zugrichtung, −1 (links) bis 1 (rechts): wohin der Kreisel fliegt */
  aim?: number
  /** Zuglänge 0–1 */
  pull?: number
  /** Tempo des letzten Rucks 0–1 */
  speed?: number
}

/** Neues Match gegen einen KI-Gegner. Der Gegner steht sofort fest, dann wird angespinnt. */
export function createBotMatch(beybladeId: number, arenaId?: string, variantId?: string) {
  return request<MatchDto>('POST', '/matches', { beybladeId, arenaId, variantId })
}

/** Eigenen Start abgeben. Hat der Gegner schon angespinnt, kommt der Kampfbericht mit. */
export function submitLaunch(matchId: string, input: LaunchInput) {
  return request<MatchDto>('POST', `/matches/${encodeURIComponent(matchId)}/launch`, input)
}

/** Ult auslösen bei Kampfzeit `t` (ms). Antwort: der ab dem Druck neu gerechnete Kampf. */
export function triggerUlt(matchId: string, t: number) {
  return request<MatchDto>('POST', `/matches/${encodeURIComponent(matchId)}/ult`, {
    t: Math.max(0, Math.round(t)),
  })
}

/** Kampf zu Ende gesehen: Ergebnis werten und speichern lassen. */
export function finishMatch(matchId: string) {
  return request<MatchDto>('POST', `/matches/${encodeURIComponent(matchId)}/finish`)
}

export function fetchMatch(matchId: string) {
  return get<MatchDto>(`/matches/${encodeURIComponent(matchId)}`)
}

/** Gespeicherter Kampf inklusive Timeline (Replay per Seed). */
export function fetchBattle(id: number) {
  return get<BattleReport>(`/battles/${id}`)
}

export function fetchBattles(limit = 10) {
  return get<BattleReport[]>(`/me/battles?limit=${limit}`)
}

/** Liga-Stand. `division` 1–3 (III ist die unterste), in der Meister-Liga `null`. */
export interface Rank {
  rating: number
  tier: string
  division: number | null
  league: string
  divisionFloor: number
  progress: number
  nextLeague: string | null
  pointsToNext: number | null
  level: number
  xp: number
  xpLevelStart: number
  xpNextLevel: number
}

export interface LeaderboardEntry {
  rank: number
  username: string
  /** alle Kämpfe, auch gegen die KI */
  wins: number
  losses: number
  ranking: Rank
}

export interface Leaderboard {
  entries: LeaderboardEntry[]
  me: LeaderboardEntry | null
  totalPlayers: number
  empty: boolean
}

export function fetchLeaderboard(limit = 10) {
  return get<Leaderboard>(`/leaderboard?limit=${limit}`)
}

export interface PlayerStats {
  username: string
  /** Siege gegen echte Spieler (Rangliste) */
  wins: number
  losses: number
  winRate: number
  rank: number | null
  /** Bilanz gegen KI-Gegner */
  botWins?: number
  botLosses?: number
  ranking: Rank
}

export function fetchStats() {
  return get<PlayerStats>('/me/stats')
}

export type LobbyStatus = 'OPEN' | 'READY' | 'FINISHED'

export interface LobbyDto {
  id: string
  name: string
  host: string
  guest: string | null
  status: LobbyStatus
  createdAt: string
  result: BattleReport | null
}

export function fetchLobbies() {
  return get<LobbyDto[]>('/lobbies')
}

export function fetchLobby(id: string) {
  return get<LobbyDto>(`/lobbies/${encodeURIComponent(id)}`)
}

export function createLobby(name: string, beybladeId: number) {
  return request<LobbyDto>('POST', '/lobbies', { name, beybladeId })
}

export function joinLobby(id: string, beybladeId: number) {
  return request<LobbyDto>('POST', `/lobbies/${encodeURIComponent(id)}/join`, { beybladeId })
}

export function startLobby(id: string) {
  return request<BattleReport>('POST', `/lobbies/${encodeURIComponent(id)}/start`)
}

export function leaveLobby(id: string) {
  return request<void>('DELETE', `/lobbies/${encodeURIComponent(id)}`)
}

export interface AuthResponse {
  token: string
  username: string
  message: string
}

export function register(username: string, password: string) {
  return request<AuthResponse>('POST', '/auth/register', { username, password })
}

export function login(username: string, password: string) {
  return request<AuthResponse>('POST', '/auth/login', { username, password })
}

export function logout() {
  return request<void>('POST', '/auth/logout')
}
