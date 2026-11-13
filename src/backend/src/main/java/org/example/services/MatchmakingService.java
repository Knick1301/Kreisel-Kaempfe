package org.example.services;

import org.example.dtos.MatchmakingDto;
import org.example.entities.User;
import org.example.game.FightMode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spielersuche: eine Warteschlange im Speicher. Wer sucht, wird mit einem Spieler mit ähnlichen Liga-Punkten
 * gepaart. Das erlaubte LP-Fenster wird mit der Wartezeit größer, nach {@link #WIDE_AFTER} passt jeder.
 * Gefundene Paare bekommen sofort ein PvP-Match ({@link MatchService#createPvpMatch}).
 *
 * <p>Der Client fragt regelmäßig nach ({@link #status}). Wer {@link #STALE} lang nicht fragt, fliegt aus der
 * Schlange, damit niemand mit einem geschlossenen Tab gepaart wird.
 */
@Service
public class MatchmakingService {

    /** LP-Fenster zu Beginn … */
    static final int WINDOW_START = 100;
    /** … wächst pro Sekunde Wartezeit um so viel … */
    static final int WINDOW_PER_SECOND = 15;
    /** … und ist nach dieser Zeit unbegrenzt. */
    static final Duration WIDE_AFTER = Duration.ofSeconds(45);
    /** Ohne Nachfrage so lange in der Schlange. */
    static final Duration STALE = Duration.ofSeconds(15);
    /** Gefundene Paare bleiben so lange abrufbar. */
    static final Duration FOUND_KEEP = Duration.ofSeconds(60);

    private final Map<String, Ticket> tickets = new LinkedHashMap<>();
    private final MatchService matches;
    private final UserService users;
    private final BeybladeService beyblades;
    private final ArenaRoomService rooms;
    private Clock clock = Clock.systemUTC();

    public MatchmakingService(MatchService matches, UserService users, BeybladeService beyblades,
                              ArenaRoomService rooms) {
        this.matches = matches;
        this.users = users;
        this.beyblades = beyblades;
        this.rooms = rooms;
    }

    /** Für Tests: Uhr setzen. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    /** In die Schlange (ein laufendes Suchen desselben Spielers wird ersetzt). */
    public MatchmakingDto join(String username, Long beybladeId) {
        return join(username, beybladeId, FightMode.CLASSIC);
    }

    /** @param mode gepaart wird nur innerhalb desselben Modus, nach dem passenden Rang */
    public synchronized MatchmakingDto join(String username, Long beybladeId, FightMode mode) {
        User me = users.get(username);
        beyblades.owned(me, beybladeId);
        Instant now = clock.instant();
        int rating = mode == FightMode.ARENA ? ArenaService.rating(me) : RankingService.rating(me);
        tickets.put(username, new Ticket(username, beybladeId, rating, mode, now, now));
        pair();
        return status(username);
    }

    /** Stand der Suche. Zählt als Lebenszeichen. */
    public synchronized MatchmakingDto status(String username) {
        Ticket t = tickets.get(username);
        if (t == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Du suchst gerade keinen Gegner.");
        }
        t.lastSeen = clock.instant();
        expire();
        pair();
        long waited = Duration.between(t.joined, clock.instant()).toMillis();
        return new MatchmakingDto(t.matchId != null ? "FOUND" : "WAITING", waited, t.matchId, waiting(t.mode),
                t.mode.name());
    }

    /** Suche abbrechen. Ein schon gefundenes Match bleibt bestehen. */
    public synchronized void cancel(String username) {
        Ticket t = tickets.get(username);
        if (t != null && t.matchId == null) tickets.remove(username);
    }

    /** Erlaubter LP-Abstand nach dieser Wartezeit. */
    static int window(Duration waited) {
        if (waited.compareTo(WIDE_AFTER) >= 0) return Integer.MAX_VALUE;
        return WINDOW_START + (int) (WINDOW_PER_SECOND * waited.toMillis() / 1000);
    }

    private int waiting(FightMode mode) {
        return (int) tickets.values().stream().filter(t -> t.matchId == null && t.mode == mode).count();
    }

    private void expire() {
        Instant now = clock.instant();
        tickets.values().removeIf(t -> t.matchId == null
                ? now.isAfter(t.lastSeen.plus(STALE))
                : now.isAfter(t.foundAt.plus(FOUND_KEEP)));
    }

    /** Wartende der Reihe nach paaren: wer am längsten wartet, zuerst. */
    private void pair() {
        Instant now = clock.instant();
        List<Ticket> open = new ArrayList<>(tickets.values().stream().filter(t -> t.matchId == null).toList());
        open.sort(Comparator.comparing(t -> t.joined));
        for (int i = 0; i < open.size(); i++) {
            Ticket a = open.get(i);
            if (a.matchId != null) continue;
            for (int j = i + 1; j < open.size(); j++) {
                Ticket b = open.get(j);
                if (b.matchId != null || b.mode != a.mode) continue;
                int allowed = Math.max(window(Duration.between(a.joined, now)),
                        window(Duration.between(b.joined, now)));
                if (Math.abs(a.rating - b.rating) > allowed) continue;
                String id = a.mode == FightMode.ARENA
                        ? rooms.createPvp(a.username, a.beybladeId, b.username, b.beybladeId)
                        : matches.createPvpMatch(a.username, a.beybladeId, b.username, b.beybladeId, null);
                a.found(id, now);
                b.found(id, now);
                break;
            }
        }
    }

    private static final class Ticket {
        final String username;
        final Long beybladeId;
        final int rating;
        final FightMode mode;
        final Instant joined;
        Instant lastSeen;
        String matchId;
        Instant foundAt;

        Ticket(String username, Long beybladeId, int rating, FightMode mode, Instant joined, Instant lastSeen) {
            this.username = username;
            this.beybladeId = beybladeId;
            this.rating = rating;
            this.mode = mode;
            this.joined = joined;
            this.lastSeen = lastSeen;
        }

        void found(String matchId, Instant at) {
            this.matchId = matchId;
            this.foundAt = at;
        }
    }
}
