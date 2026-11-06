package org.example.services;

import org.example.dtos.LeaderboardDto;
import org.example.dtos.LeaderboardEntry;
import org.example.dtos.PlayerStatsDto;
import org.example.entities.User;
import org.example.repositories.BattleResultRepository;
import org.example.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Rangliste nach Liga-Punkten, bei Gleichstand nach Siegen. */
@Service
public class LeaderboardService {

    private final BattleResultRepository results;
    private final UserRepository users;
    private final RankingService ranking;

    public LeaderboardService(BattleResultRepository results, UserRepository users, RankingService ranking) {
        this.results = results;
        this.users = users;
        this.ranking = ranking;
    }

    @Transactional(readOnly = true)
    public LeaderboardDto leaderboard(String username, int limit) {
        List<LeaderboardEntry> all = ranking();
        LeaderboardEntry me = find(all, username);
        return new LeaderboardDto(all.stream().limit(limit).toList(), me, all.size(), all.isEmpty());
    }

    /** Siege und Niederlagen gegen echte Spieler, KI-Bilanz getrennt, dazu Liga und Level. */
    @Transactional(readOnly = true)
    public PlayerStatsDto stats(String username) {
        LeaderboardEntry me = find(ranking(), username);
        long[] pvp = pvpCounts(username);
        long botWins = results.countBotWins(username);
        long botLosses = results.countBotLosses(username);
        int winRate = pvp[0] + pvp[1] == 0 ? 0 : (int) Math.round(100.0 * pvp[0] / (pvp[0] + pvp[1]));
        var rank = users.findByUsername(username).map(ranking::rankOf)
                .orElseGet(() -> RankingService.rankOf(RankingService.START_RATING, 0));
        return new PlayerStatsDto(username, pvp[0], pvp[1], winRate, me == null ? null : me.rank(),
                botWins, botLosses, rank);
    }

    List<LeaderboardEntry> ranking() {
        Map<String, long[]> counts = new HashMap<>();
        for (Object[] row : results.countAllWinsByUser()) {
            counts.computeIfAbsent((String) row[0], k -> new long[2])[0] = ((Number) row[1]).longValue();
        }
        for (Object[] row : results.countAllLossesByUser()) {
            counts.computeIfAbsent((String) row[0], k -> new long[2])[1] = ((Number) row[1]).longValue();
        }

        List<User> rated = new ArrayList<>(users.findByRatingIsNotNull());
        rated.sort(Comparator.<User>comparingInt(u -> -RankingService.rating(u))
                .thenComparingLong(u -> -counts.getOrDefault(u.getUsername(), new long[2])[0])
                .thenComparing(User::getUsername));

        List<LeaderboardEntry> entries = new ArrayList<>();
        for (int i = 0; i < rated.size(); i++) {
            User u = rated.get(i);
            long[] c = counts.getOrDefault(u.getUsername(), new long[2]);
            int place = i + 1;
            if (i > 0) {
                LeaderboardEntry prev = entries.get(i - 1);
                if (prev.ranking().rating() == RankingService.rating(u) && prev.wins() == c[0]) {
                    place = prev.rank();
                }
            }
            entries.add(new LeaderboardEntry(place, u.getUsername(), c[0], c[1], ranking.rankOf(u)));
        }
        return entries;
    }

    private long[] pvpCounts(String username) {
        long[] c = new long[2];
        for (Object[] row : results.countWinsByUser()) {
            if (username.equals(row[0])) c[0] = ((Number) row[1]).longValue();
        }
        for (Object[] row : results.countLossesByUser()) {
            if (username.equals(row[0])) c[1] = ((Number) row[1]).longValue();
        }
        return c;
    }

    private static LeaderboardEntry find(List<LeaderboardEntry> entries, String username) {
        return entries.stream().filter(e -> e.username().equals(username)).findFirst().orElse(null);
    }
}
