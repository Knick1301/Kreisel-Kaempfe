package org.example.services;

import org.example.dtos.ArenaDto;
import org.example.dtos.BattleReportDto;
import org.example.dtos.LaunchChallengeDto;
import org.example.dtos.MatchDto;
import org.example.dtos.MatchupDto;
import org.example.entities.BattleResult;
import org.example.entities.Beyblade;
import org.example.entities.FighterSnapshot;
import org.example.entities.User;
import org.example.game.Arena;
import org.example.game.BattleMode;
import org.example.game.BattleTuning;
import org.example.game.EventType;
import org.example.game.BotLauncher;
import org.example.game.Launch;
import org.example.game.LaunchChallenge;
import org.example.game.LaunchInput;
import org.example.game.LaunchRules;
import org.example.game.MatchStatus;
import org.example.game.Side;
import org.example.game.StyleVector;
import org.example.game.UltInput;
import org.example.repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Match-Ablauf mit Anspinnen: Match anlegen, beide Seiten spinnen verdeckt an, dann wird simuliert.
 *
 * <p>Ein Match hat zwei gleichwertige Plätze. Der KI-Gegner ist nur ein Platz, der seinen Start sofort
 * abgibt, ohne den des Spielers zu kennen. Für PvP setzt sich später ein zweiter Mensch auf Platz B und
 * gibt seinen Start über denselben Endpunkt ab, der Ablauf bleibt gleich. Wer bis zum Ablauf der Frist
 * nicht angespinnt hat, bekommt einen verpassten Start, damit niemand ein Match blockieren kann.
 *
 * <p>Nach dem Anspinnen läuft der Kampf ({@link MatchStatus#FIGHT}): Er ist nur vorläufig simuliert, die volle
 * Ult-Leiste des Spielers wartet, bis er drückt ({@link #ult}). Jeder Druck simuliert mit demselben Seed neu,
 * bis zum Druck bleibt der Kampf deshalb identisch. Gewertet und gespeichert wird erst am Ende ({@link #finish})
 * oder, wenn der Spieler verschwindet, spätestens nach Kampfdauer plus {@link #FIGHT_GRACE}.
 *
 * <p>Matches leben wie Lobbys nur im Speicher, sie dauern Sekunden. Gespeichert wird der fertige Kampf.
 */
@Service
public class MatchService {

    /** Abgeschlossene oder liegengebliebene Matches werden danach aufgeräumt. */
    static final Duration RETENTION = Duration.ofMinutes(10);
    /** Spielraum nach dem (vorläufigen) Kampfende für Einblendungen und Netz, danach wird gewertet. */
    static final Duration FIGHT_GRACE = Duration.ofSeconds(90);

    private final Map<String, Match> matches = new ConcurrentHashMap<>();
    private final BattleService battles;
    private final BeybladeService beyblades;
    private final UserRepository users;
    private final Random random = new Random();
    private Clock clock = Clock.systemUTC();

    public MatchService(BattleService battles, BeybladeService beyblades, UserRepository users) {
        this.battles = battles;
        this.beyblades = beyblades;
        this.users = users;
    }

    /** Für Tests: Uhr setzen, um die Frist zu prüfen. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    /** Neues Match gegen einen KI-Gegner. Der Gegner steht sofort fest, das Anspinnen kommt danach. */
    public MatchDto createBotMatch(String username, Long beybladeId, String arenaId) {
        return createBotMatch(username, beybladeId, arenaId, null);
    }

    /** @param variantId optische Variante der Arena, {@code null} für die Vorgabe */
    public MatchDto createBotMatch(String username, Long beybladeId, String arenaId, String variantId) {
        Arena arena = BattleService.selectArena(arenaId);
        String variant = BattleService.selectVariant(arena, variantId);
        User me = user(username);
        Beyblade mine = beyblades.owned(me, beybladeId);
        User bot = battles.botUser();
        FighterSnapshot botFighter = battles.drawBot(mine.getStats());

        cleanUp(username);
        Instant now = clock.instant();
        LaunchChallenge challenge = LaunchChallenge.random(random);
        Match m = new Match(UUID.randomUUID().toString(), BattleMode.BOT, arena, variant, challenge, now,
                now.plusMillis(LaunchRules.SUBMIT_TIMEOUT_MS),
                new Seat(Side.A, me.getId(), me.getUsername(), FighterSnapshot.of(me, mine), true),
                new Seat(Side.B, bot.getId(), bot.getUsername(), botFighter, false));
        // Die KI spinnt sofort an, verdeckt und ohne die Eingabe des Spielers zu kennen.
        m.b.submit(BotLauncher.input(challenge, random, LaunchRules.BOT_SKILL));
        matches.put(m.id, m);
        return dto(m, m.a, false);
    }

    public MatchDto get(String id, String username) {
        Match m = match(id);
        Seat me = m.seatOf(username);
        resolveIfDue(m);
        return dto(m, me, true);
    }

    /** Eigenen Start abgeben. Sobald beide Starts da sind, wird der Kampf simuliert und mitgeliefert. */
    public MatchDto launch(String id, String username, LaunchInput input) {
        Match m = match(id);
        Seat me = m.seatOf(username);
        synchronized (m) {
            if (me.submitted) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Du hast schon angespinnt.");
            }
            if (m.status == MatchStatus.DONE) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Zu spät, der Kampf ist schon entschieden.");
            }
            me.submit(input == null ? LaunchInput.NONE : input);
        }
        resolveIfDue(m);
        return dto(m, me, true);
    }

    /**
     * Ult auslösen. {@code t} ist die Kampfzeit (ms) beim Druck. Gilt nur, wenn die Leiste zu diesem Zeitpunkt
     * voll ist und der Kampf noch läuft, sonst 409.
     */
    public MatchDto ult(String id, String username, int t) {
        Match m = match(id);
        Seat me = m.seatOf(username);
        resolveIfDue(m);
        synchronized (m) {
            if (m.status != MatchStatus.FIGHT) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Der Kampf läuft nicht.");
            }
            List<Integer> before = me.ult.times();
            if (t < 0 || t >= m.fight.getDurationMs()
                    || (!before.isEmpty() && t <= before.getLast())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Zu spät für die Ult.");
            }
            UltInput next = me.ult.with(t);
            BattleResult r = simulate(m, me == m.a ? next : m.a.ult, me == m.b ? next : m.b.ult);
            boolean fired = battles.replay(r).map(o -> o.timeline().events().stream()
                    .anyMatch(e -> e.type() == EventType.ULT && e.side() == me.side
                            && e.t() >= t && e.t() < t + BattleTuning.TICK_MS)).orElse(false);
            if (!fired) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Die Ult ist noch nicht bereit.");
            }
            me.ult = next;
            m.fight = r;
        }
        return dto(m, me, true);
    }

    /** Kampf zu Ende gesehen: werten und speichern. Mehrfach aufrufbar. */
    public MatchDto finish(String id, String username) {
        Match m = match(id);
        Seat me = m.seatOf(username);
        resolveIfDue(m);
        synchronized (m) {
            if (m.status == MatchStatus.LAUNCH) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Erst anspinnen.");
            }
            commit(m);
        }
        return dto(m, me, true);
    }

    /**
     * Nach dem Anspinnen vorläufig simulieren (FIGHT), bei liegengebliebenen Kämpfen werten (DONE).
     * Läuft synchronisiert pro Match.
     */
    private void resolveIfDue(Match m) {
        synchronized (m) {
            if (m.status == MatchStatus.FIGHT) {
                Instant due = m.fightStart.plusMillis(m.fight.getDurationMs()).plus(FIGHT_GRACE);
                if (clock.instant().isAfter(due)) commit(m);
                return;
            }
            if (m.status != MatchStatus.LAUNCH) return;
            boolean expired = clock.instant().isAfter(m.deadline);
            if (!(m.a.submitted && m.b.submitted) && !expired) return;
            m.launchA = m.a.submitted ? m.challenge.score(m.a.input) : Launch.MISSED;
            m.launchB = m.b.submitted ? m.challenge.score(m.b.input) : Launch.MISSED;
            m.seed = battles.newSeed();
            m.fight = simulate(m, m.a.ult, m.b.ult);
            m.fightStart = clock.instant();
            m.status = MatchStatus.FIGHT;
        }
    }

    private BattleResult simulate(Match m, UltInput ultA, UltInput ultB) {
        return battles.simulate(m.mode, m.arena, m.variant,
                m.a.userId, m.a.snapshot.withLaunch(m.launchA).withUltInput(ultA),
                m.b.userId, m.b.snapshot.withLaunch(m.launchB).withUltInput(ultB), m.seed);
    }

    /** Vorläufigen Kampf werten und speichern, genau einmal. */
    private void commit(Match m) {
        if (m.status != MatchStatus.FIGHT) return;
        m.battleId = battles.commit(m.fight).getId();
        m.status = MatchStatus.DONE;
    }

    private MatchDto dto(Match m, Seat me, boolean withReport) {
        Seat them = me == m.a ? m.b : m.a;
        StyleVector styleA = StyleVector.of(m.a.snapshot.stats());
        StyleVector styleB = StyleVector.of(m.b.snapshot.stats());
        long left = Math.max(0, Duration.between(clock.instant(), m.deadline).toMillis());
        BattleReportDto report = !withReport ? null
                : m.battleId != null ? battles.report(m.battleId)
                : m.fight != null ? battles.report(m.fight) : null;
        return new MatchDto(m.id, m.mode, m.status, me.side, ArenaDto.from(m.arena, m.variant),
                List.of(battles.fighterDto(Side.A, m.a.snapshot, m.mode),
                        battles.fighterDto(Side.B, m.b.snapshot, m.mode)),
                MatchupDto.of(styleA, styleB), LaunchChallengeDto.from(m.challenge),
                me.submitted, them.submitted, m.status == MatchStatus.LAUNCH ? left : 0, m.battleId, report);
    }

    private Match match(String id) {
        Match m = id == null ? null : matches.get(id);
        if (m == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Match nicht gefunden.");
        return m;
    }

    /**
     * Alte Matches entfernen, dazu offene Matches desselben Spielers (er hat ein neues gestartet). Laufende
     * Kämpfe werden vorher gewertet, sonst könnte man einer Niederlage durch Neustart ausweichen.
     */
    private void cleanUp(String username) {
        Instant cutoff = clock.instant().minus(RETENTION);
        for (Match m : List.copyOf(matches.values())) {
            boolean old = m.createdAt.isBefore(cutoff);
            boolean mine = m.a.username.equals(username) && m.status != MatchStatus.DONE;
            if (!old && !mine) continue;
            synchronized (m) {
                commit(m);
            }
            matches.remove(m.id);
        }
    }

    private User user(String username) {
        return users.findByUsername(username).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User " + username + " nicht gefunden"));
    }

    // ---------------------------------------------------------------- Zustand

    private static final class Match {
        final String id;
        final BattleMode mode;
        final Arena arena;
        final String variant;
        final LaunchChallenge challenge;
        final Instant createdAt;
        final Instant deadline;
        final Seat a;
        final Seat b;
        MatchStatus status = MatchStatus.LAUNCH;
        Long battleId;
        Launch launchA;
        Launch launchB;
        long seed;
        /** vorläufiger Kampf (FIGHT), noch nicht gespeichert */
        BattleResult fight;
        Instant fightStart;

        Match(String id, BattleMode mode, Arena arena, String variant, LaunchChallenge challenge, Instant createdAt,
              Instant deadline, Seat a, Seat b) {
            this.id = id;
            this.mode = mode;
            this.arena = arena;
            this.variant = variant;
            this.challenge = challenge;
            this.createdAt = createdAt;
            this.deadline = deadline;
            this.a = a;
            this.b = b;
        }

        /** Platz des Spielers. Fremde bekommen 404, damit Match-IDs nichts verraten. */
        Seat seatOf(String username) {
            if (a.username.equals(username)) return a;
            if (b.username.equals(username) && mode == BattleMode.PVP) return b;
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Match nicht gefunden.");
        }
    }

    private static final class Seat {
        final Side side;
        final Long userId;
        final String username;
        final FighterSnapshot snapshot;
        LaunchInput input;
        boolean submitted;
        /** Menschen lösen ihre Ult selbst aus, die KI automatisch */
        UltInput ult;

        Seat(Side side, Long userId, String username, FighterSnapshot snapshot, boolean human) {
            this.side = side;
            this.userId = userId;
            this.username = username;
            this.snapshot = snapshot;
            this.ult = human ? UltInput.manual(List.of()) : UltInput.AUTO;
        }

        void submit(LaunchInput in) {
            this.input = in;
            this.submitted = true;
        }
    }
}
