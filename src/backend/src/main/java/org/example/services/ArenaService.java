package org.example.services;

import org.example.dtos.ArenaResultDto;
import org.example.dtos.LeaderboardDto;
import org.example.dtos.LeaderboardEntry;
import org.example.dtos.RankDto;
import org.example.entities.User;
import org.example.game.Side;
import org.example.repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Arena-Modus: eigener Rang, getrennt vom klassischen. Die Physik läuft auf dem Server
 * ({@link ArenaRoomService}), gewertet wird hier, sobald ein Raum entschieden ist. Gegen die KI gibt es wie im
 * klassischen Modus nur bis Gold Liga-Punkte, gegen Spieler volle Elo, im Cup nur XP.
 */
@Service
public class ArenaService {

    private final UserRepository users;

    public ArenaService(UserRepository users) {
        this.users = users;
    }

    /**
     * Ergebnis eines Arena-Kampfs verbuchen.
     *
     * @param userB  {@code null} bei einem KI-Gegner
     * @param ranked {@code false}: nur XP (Cup)
     * @return Änderung und neuer Stand je Seite, {@code null} für die KI
     */
    @Transactional
    public Map<Side, ArenaResultDto> apply(String userA, String userB, Side winner, boolean ranked) {
        User a = user(userA);
        User b = userB == null ? null : user(userB);
        int beforeA = rating(a);
        int deltaA;
        int deltaB = 0;
        if (!ranked) {
            deltaA = 0;
        } else if (b == null) {
            int d = beforeA >= RankingService.BOT_RATING_CAP ? 0
                    : (int) Math.round(RankingService.BOT_FACTOR * RankingService.delta(beforeA, beforeA));
            deltaA = winner == Side.A ? d : -d;
        } else {
            int beforeB = rating(b);
            int d = winner == Side.A ? RankingService.delta(beforeA, beforeB) : RankingService.delta(beforeB, beforeA);
            deltaA = winner == Side.A ? d : -d;
            deltaB = -deltaA;
        }
        Map<Side, ArenaResultDto> out = new java.util.EnumMap<>(Side.class);
        out.put(Side.A, book(a, deltaA, winner == Side.A, b != null));
        if (b != null) out.put(Side.B, book(b, deltaB, winner == Side.B, true));
        return out;
    }

    private ArenaResultDto book(User u, int delta, boolean won, boolean pvp) {
        int before = rating(u);
        int after = Math.max(RankingService.START_RATING, before + delta);
        int xp = (won ? RankingService.XP_WIN : RankingService.XP_LOSS) * (pvp ? 2 : 1);
        u.setArenaRating(after);
        u.setArenaXp(xp(u) + xp);
        if (won) u.setArenaWins(count(u.getArenaWins()) + 1);
        else u.setArenaLosses(count(u.getArenaLosses()) + 1);
        users.save(u);
        return new ArenaResultDto(after - before, xp, RankingService.rankOf(after, xp(u)));
    }

    /** Rangliste des Arena-Modus, wie die klassische aufgebaut. */
    @Transactional(readOnly = true)
    public LeaderboardDto leaderboard(String username, int limit) {
        List<User> rated = new ArrayList<>(users.findByArenaRatingIsNotNull());
        rated.sort(Comparator.<User>comparingInt(u -> -rating(u))
                .thenComparingInt(u -> -count(u.getArenaWins()))
                .thenComparing(User::getUsername));
        List<LeaderboardEntry> entries = new ArrayList<>();
        for (int i = 0; i < rated.size(); i++) {
            User u = rated.get(i);
            int place = i + 1;
            if (i > 0) {
                LeaderboardEntry prev = entries.get(i - 1);
                if (prev.ranking().rating() == rating(u) && prev.wins() == count(u.getArenaWins())) place = prev.rank();
            }
            entries.add(new LeaderboardEntry(place, u.getUsername(), count(u.getArenaWins()),
                    count(u.getArenaLosses()), RankingService.rankOf(rating(u), xp(u))));
        }
        LeaderboardEntry me = entries.stream().filter(e -> e.username().equals(username)).findFirst().orElse(null);
        return new LeaderboardDto(entries.stream().limit(limit).toList(), me, entries.size(), entries.isEmpty());
    }

    /** Eigener Eintrag in der Arena-Rangliste, {@code null} ohne Arena-Kampf. */
    @Transactional(readOnly = true)
    public LeaderboardEntry standing(String username) {
        return leaderboard(username, 0).me();
    }

    /** Arena-Rang eines Spielers ohne Platzierung (für Freundeslisten), {@code null} ohne Arena-Kampf. */
    public static RankDto rankOf(User u) {
        return u.getArenaRating() == null ? null : RankingService.rankOf(rating(u), xp(u));
    }

    /** Arena-Liga-Punkte, ohne Arena-Kampf der Startwert. */
    public static int rating(User u) {
        return u.getArenaRating() == null ? RankingService.START_RATING
                : Math.max(RankingService.START_RATING, u.getArenaRating());
    }

    private static int xp(User u) {
        return u.getArenaXp() == null ? 0 : u.getArenaXp();
    }

    private static int count(Integer v) {
        return v == null ? 0 : v;
    }

    private User user(String username) {
        return users.findByUsername(username).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User " + username + " nicht gefunden"));
    }
}
