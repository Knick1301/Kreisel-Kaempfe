package org.example.services;

import org.example.dtos.FighterDto;
import org.example.dtos.TournamentDto;
import org.example.dtos.TournamentDto.CupEntry;
import org.example.dtos.TournamentDto.CupPairing;
import org.example.entities.Beyblade;
import org.example.entities.FighterSnapshot;
import org.example.entities.User;
import org.example.game.Arena;
import org.example.game.Battle;
import org.example.game.BattleMode;
import org.example.game.BattleOutcome;
import org.example.game.BattleTuning;
import org.example.game.BotLauncher;
import org.example.game.LaunchChallenge;
import org.example.game.Side;
import org.example.game.UltInput;
import org.example.repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Solo-Cup: 8er-K.-o.-Baum, der Spieler gegen sieben KI-Gegner. Wer später auf den Spieler treffen kann, ist
 * stärker (bessere Teile, besseres Anspinnen). Die eigenen Kämpfe laufen als Match mit Anspinnen und Ult, aber
 * ohne Liga-Punkte. Die übrigen Paarungen einer Runde rechnet der Server aus, sobald der Spieler fertig ist.
 */
@Service
public class TournamentService {

    public static final int SIZE = 8;
    public static final int ROUNDS = 3;
    /** Bonus-XP für den Cup-Sieg */
    public static final int XP_CUP = 300;
    static final Duration TTL = Duration.ofHours(2);

    /** Stufe je Runde, in der der Gegner frühestens auf den Spieler trifft: Faktor aufs Stat-Budget */
    static final double[] STRENGTH = {0.95, 1.06, 1.15};
    /** Stufe je Runde: Genauigkeit beim Anspinnen */
    static final double[] SKILL = {0.35, 0.6, 0.85};
    /** Stufe je Runde: Reaktionszeit der Arena-KI in ms (kleiner = schneller) */
    static final int[] REACTION_MS = {200, 150, 110};
    static final String[] ROUND_NAMES = {"Viertelfinale", "Halbfinale", "Finale"};

    public enum Format { CLASSIC, ARENA }

    public enum Status { RUNNING, WON, OUT }

    private final Map<String, Cup> cups = new ConcurrentHashMap<>();
    private final UserRepository users;
    private final BeybladeService beyblades;
    private final BattleService battles;
    private final MatchService matches;
    private final ArenaRoomService rooms;
    private final Random random = new Random();
    private Clock clock = Clock.systemUTC();

    public TournamentService(UserRepository users, BeybladeService beyblades, BattleService battles,
                             MatchService matches, ArenaRoomService rooms) {
        this.users = users;
        this.beyblades = beyblades;
        this.battles = battles;
        this.matches = matches;
        this.rooms = rooms;
    }

    // ---------------------------------------------------------------- Ablauf

    /** Neuer Cup, ein laufender Cup des Spielers wird verworfen. */
    @Transactional(readOnly = true)
    public TournamentDto start(String username, Long beybladeId, String formatName) {
        Format format = format(formatName);
        User me = user(username);
        Beyblade mine = beyblades.owned(me, beybladeId);
        Cup cup = new Cup(UUID.randomUUID().toString(), username, beybladeId, format, clock.instant());
        int playerSlot = random.nextInt(SIZE);
        for (int slot = 0; slot < SIZE; slot++) {
            if (slot == playerSlot) {
                cup.entries.add(new Entry(FighterSnapshot.of(me, mine), true, 0));
            } else {
                int level = meetRound(slot, playerSlot);
                cup.entries.add(new Entry(battles.drawBot(mine.getStats(), STRENGTH[level]), false, level));
            }
        }
        cup.alive = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5, 6, 7));
        cups.put(username, cup);
        return dto(cup);
    }

    /** Aktueller Cup, offene eigene Kämpfe werden dabei nachgetragen. */
    @Transactional
    public TournamentDto current(String username) {
        Cup cup = cup(username);
        synchronized (cup) {
            advance(cup);
        }
        return dto(cup);
    }

    /** Match für den nächsten eigenen Kampf anlegen (oder das laufende zurückgeben). */
    @Transactional
    public TournamentDto next(String username) {
        Cup cup = cup(username);
        synchronized (cup) {
            advance(cup);
            if (cup.status != Status.RUNNING) throw conflict("Der Cup ist vorbei.");
            if (cup.matchId == null) {
                int opp = opponentSlot(cup);
                Entry o = cup.entries.get(opp);
                cup.matchId = cup.format == Format.ARENA
                        ? rooms.createCup(username, cup.beybladeId, o.snapshot, SKILL[o.level], REACTION_MS[o.level])
                        : matches.createCupMatch(username, cup.beybladeId, o.snapshot, SKILL[o.level]);
            }
        }
        return dto(cup);
    }

    /** Eigenen Kampf auswerten, dann die übrigen Paarungen der Runde ausrechnen. */
    private void advance(Cup cup) {
        if (cup.status != Status.RUNNING || cup.matchId == null) return;
        boolean arena = cup.format == Format.ARENA;
        Side winner = arena ? rooms.winnerIfDone(cup.matchId) : matches.winnerIfDone(cup.matchId);
        if (winner == null) return;
        String finish = arena ? rooms.finishIfDone(cup.matchId)
                : java.util.Optional.ofNullable(matches.finishIfDone(cup.matchId)).map(Enum::name).orElse(null);
        int me = playerSlot(cup);
        int opp = opponentSlot(cup);
        boolean won = winner == Side.A;
        cup.matchId = null;

        List<CupPairing> round = new ArrayList<>();
        List<Integer> next = new ArrayList<>();
        for (int i = 0; i < cup.alive.size(); i += 2) {
            int a = cup.alive.get(i);
            int b = cup.alive.get(i + 1);
            int w;
            String fin;
            if (a == me || b == me) {
                w = won ? me : opp;
                fin = finish;
            } else {
                BattleOutcome o = simulate(cup.entries.get(a), cup.entries.get(b));
                w = o.winnerSide() == Side.A ? a : b;
                fin = o.finish().name();
            }
            round.add(new CupPairing(entry(cup, a), entry(cup, b), w == a ? "A" : "B", fin));
            next.add(w);
        }
        cup.results.add(round);
        cup.alive = next;

        if (!won) {
            cup.status = Status.OUT;
        } else if (cup.alive.size() == 1) {
            cup.status = Status.WON;
            User u = user(cup.username);
            u.setCupsWon(u.getCupsWon() + 1);
            u.setXp((u.getXp() == null ? 0 : u.getXp()) + XP_CUP);
            users.save(u);
        }
    }

    /** Nebenpaarung zwischen zwei KI-Gegnern, beide mit ihrer Stufe angesponnen. */
    private BattleOutcome simulate(Entry a, Entry b) {
        LaunchChallenge c = LaunchChallenge.random(random);
        var la = c.score(BotLauncher.input(c, random, SKILL[a.level]));
        var lb = c.score(BotLauncher.input(c, random, SKILL[b.level]));
        List<Arena> all = Arena.all();
        return new Battle(all.get(random.nextInt(all.size())), a.snapshot.fighter(), b.snapshot.fighter(),
                random.nextLong(), la, lb, BattleTuning.SIM_VERSION, UltInput.AUTO, UltInput.AUTO).run();
    }

    // ---------------------------------------------------------------- Baum

    /** In welcher Runde (0–2) treffen zwei Plätze im 8er-Baum frühestens aufeinander? */
    static int meetRound(int slotA, int slotB) {
        int round = 0;
        while ((slotA >> (round + 1)) != (slotB >> (round + 1))) round++;
        return round;
    }

    private static int playerSlot(Cup cup) {
        for (int i = 0; i < cup.entries.size(); i++) if (cup.entries.get(i).player) return i;
        throw new IllegalStateException("Cup ohne Spieler");
    }

    private static int opponentSlot(Cup cup) {
        int me = playerSlot(cup);
        int idx = cup.alive.indexOf(me);
        return cup.alive.get(idx % 2 == 0 ? idx + 1 : idx - 1);
    }

    // ---------------------------------------------------------------- Ausgabe

    private TournamentDto dto(Cup cup) {
        int round = cup.results.size();
        FighterDto opponent = null;
        String roundName = null;
        List<CupPairing> upcoming = new ArrayList<>();
        if (cup.status == Status.RUNNING) {
            opponent = battles.fighterDto(Side.B, cup.entries.get(opponentSlot(cup)).snapshot, BattleMode.BOT);
            roundName = ROUND_NAMES[round];
            for (int i = 0; i < cup.alive.size(); i += 2) {
                upcoming.add(new CupPairing(entry(cup, cup.alive.get(i)), entry(cup, cup.alive.get(i + 1)), null,
                        null));
            }
        }
        return new TournamentDto(cup.id, cup.format.name(), cup.status.name(), round, roundName,
                List.copyOf(cup.results), upcoming, opponent, cup.matchId,
                cup.status == Status.WON ? XP_CUP : 0);
    }

    private static CupEntry entry(Cup cup, int slot) {
        Entry e = cup.entries.get(slot);
        return new CupEntry(slot, e.snapshot.getBeyblade(), e.player ? cup.username : null, e.player,
                e.player ? null : e.level);
    }

    // ---------------------------------------------------------------- Hilfen

    private Cup cup(String username) {
        cleanUp();
        Cup cup = cups.get(username);
        if (cup == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Kein laufender Cup.");
        return cup;
    }

    private void cleanUp() {
        Instant cutoff = clock.instant().minus(TTL);
        cups.values().removeIf(c -> c.createdAt.isBefore(cutoff));
    }

    private static Format format(String name) {
        if (name == null || name.isBlank()) return Format.CLASSIC;
        try {
            return Format.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw bad("Unbekanntes Format " + name + ".");
        }
    }

    private User user(String username) {
        return users.findByUsername(username).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User " + username + " nicht gefunden"));
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private record Entry(FighterSnapshot snapshot, boolean player, int level) {}

    private static final class Cup {
        final String id;
        final String username;
        final Long beybladeId;
        final Format format;
        final Instant createdAt;
        final List<Entry> entries = new ArrayList<>();
        /** noch im Turnier, in Baum-Reihenfolge (Nachbarn kämpfen gegeneinander) */
        List<Integer> alive;
        final List<List<CupPairing>> results = new ArrayList<>();
        Status status = Status.RUNNING;
        /** eigener Kampf der aktuellen Runde */
        String matchId;

        Cup(String id, String username, Long beybladeId, Format format, Instant createdAt) {
            this.id = id;
            this.username = username;
            this.beybladeId = beybladeId;
            this.format = format;
            this.createdAt = createdAt;
        }
    }
}
