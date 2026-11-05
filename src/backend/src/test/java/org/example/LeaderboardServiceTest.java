package org.example;

import org.example.dtos.LeaderboardDto;
import org.example.dtos.LeaderboardEntry;
import org.example.dtos.PlayerStatsDto;
import org.example.repositories.BattleResultRepository;
import org.example.services.LeaderboardService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LeaderboardServiceTest {

    private final BattleResultRepository results = mock(BattleResultRepository.class);
    private final LeaderboardService service = new LeaderboardService(results);

    private static List<Object[]> rows(Object... pairs) {
        List<Object[]> list = new java.util.ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) list.add(new Object[]{pairs[i], pairs[i + 1]});
        return list;
    }

    @Test
    void ohneKaempfeIstDieRanglisteLeer() {
        when(results.countWinsByUser()).thenReturn(List.of());
        when(results.countLossesByUser()).thenReturn(List.of());

        LeaderboardDto board = service.leaderboard("demo", 10);

        assertTrue(board.empty());
        assertNull(board.me());
        assertEquals(0, service.stats("demo").wins());
    }

    @Test
    void sortiertNachSiegenUndZeigtEigenePlatzierung() {
        when(results.countWinsByUser()).thenReturn(rows("anna", 5L, "demo", 2L, "bert", 5L));
        when(results.countLossesByUser()).thenReturn(rows("anna", 1L, "demo", 2L, "bert", 3L, "carl", 4L));

        LeaderboardDto board = service.leaderboard("demo", 10);

        assertEquals(List.of("anna", "bert", "demo", "carl"),
                board.entries().stream().map(LeaderboardEntry::username).toList());
        assertEquals(3, board.me().rank());
        assertEquals(4, board.totalPlayers());

        PlayerStatsDto stats = service.stats("demo");
        assertEquals(50, stats.winRate());
        assertEquals(3, stats.rank());
    }

    @Test
    void gleicheBilanzGleicherRang() {
        when(results.countWinsByUser()).thenReturn(rows("anna", 3L, "bert", 3L));
        when(results.countLossesByUser()).thenReturn(rows("anna", 1L, "bert", 1L));

        List<LeaderboardEntry> entries = service.leaderboard(null, 10).entries();

        assertEquals(1, entries.get(0).rank());
        assertEquals(1, entries.get(1).rank());
    }
}
