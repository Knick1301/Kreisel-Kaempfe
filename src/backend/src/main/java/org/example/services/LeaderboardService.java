package org.example.services;

import org.example.dtos.LeaderboardDto;
import org.example.dtos.LeaderboardEntry;
import org.example.dtos.PlayerStatsDto;
import org.example.repositories.BattleResultRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LeaderboardService {

    private final BattleResultRepository results;

    public LeaderboardService(BattleResultRepository results) {
        this.results = results;
    }

    public LeaderboardDto leaderboard(String username, int limit) {
        List<LeaderboardEntry> all = ranking();
        LeaderboardEntry me = find(all, username);
        return new LeaderboardDto(all.stream().limit(limit).toList(), me, all.size(), all.isEmpty());
    }

    public PlayerStatsDto stats(String username) {
        LeaderboardEntry me = find(ranking(), username);
        if (me == null) {
            return new PlayerStatsDto(username, 0, 0, 0, null);
        }
        int winRate = (int) Math.round(100.0 * me.wins() / me.battles());
        return new PlayerStatsDto(username, me.wins(), me.losses(), winRate, me.rank());
    }

    List<LeaderboardEntry> ranking() {
        Map<String, long[]> counts = new HashMap<>();
        for (Object[] row : results.countWinsByUser()) {
            counts.computeIfAbsent((String) row[0], k -> new long[2])[0] = ((Number) row[1]).longValue();
        }
        for (Object[] row : results.countLossesByUser()) {
            counts.computeIfAbsent((String) row[0], k -> new long[2])[1] = ((Number) row[1]).longValue();
        }

        List<Map.Entry<String, long[]>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Comparator.<Map.Entry<String, long[]>>comparingLong(e -> -e.getValue()[0])
                .thenComparingLong(e -> e.getValue()[1])
                .thenComparing(Map.Entry::getKey));

        List<LeaderboardEntry> entries = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            long[] c = sorted.get(i).getValue();
            int rank = i + 1;
            if (i > 0) {
                LeaderboardEntry prev = entries.get(i - 1);
                if (prev.wins() == c[0] && prev.losses() == c[1]) {
                    rank = prev.rank();
                }
            }
            entries.add(new LeaderboardEntry(rank, sorted.get(i).getKey(), c[0], c[1]));
        }
        return entries;
    }

    private static LeaderboardEntry find(List<LeaderboardEntry> entries, String username) {
        return entries.stream().filter(e -> e.username().equals(username)).findFirst().orElse(null);
    }
}
