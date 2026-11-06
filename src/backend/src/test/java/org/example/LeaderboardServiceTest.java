package org.example;

import org.example.dtos.LeaderboardDto;
import org.example.dtos.LeaderboardEntry;
import org.example.entities.User;
import org.example.repositories.BattleResultRepository;
import org.example.repositories.UserRepository;
import org.example.services.LeaderboardService;
import org.example.services.RankingService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LeaderboardServiceTest {

    private final BattleResultRepository results = mock(BattleResultRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final LeaderboardService service = new LeaderboardService(results, users, new RankingService());

    private static List<Object[]> rows(Object... pairs) {
        List<Object[]> list = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) list.add(new Object[]{pairs[i], pairs[i + 1]});
        return list;
    }

    private static User user(String name, Integer rating) {
        User u = new User();
        u.setUsername(name);
        u.setRating(rating);
        return u;
    }

    @Test
    void ohneGewerteteKaempfeIstDieRanglisteLeer() {
        when(users.findByRatingIsNotNull()).thenReturn(List.of());
        when(users.findByUsername(anyString())).thenReturn(Optional.of(user("demo", null)));
        when(results.countAllWinsByUser()).thenReturn(List.of());
        when(results.countAllLossesByUser()).thenReturn(List.of());

        LeaderboardDto board = service.leaderboard("demo", 10);

        assertTrue(board.empty());
        assertNull(board.me());
        var stats = service.stats("demo");
        assertNull(stats.rank());
        assertEquals("Bronze III", stats.ranking().league());
        assertEquals(RankingService.START_RATING, stats.ranking().rating());
    }

    @Test
    void sortiertNachLigaPunktenDannNachSiegen() {
        when(users.findByRatingIsNotNull()).thenReturn(List.of(
                user("anna", 1120), user("demo", 1260), user("bert", 1120), user("carl", 900)));
        when(results.countAllWinsByUser()).thenReturn(rows("anna", 3L, "demo", 9L, "bert", 5L));
        when(results.countAllLossesByUser()).thenReturn(rows("anna", 1L, "carl", 4L));

        LeaderboardDto board = service.leaderboard("demo", 10);

        assertEquals(List.of("demo", "bert", "anna", "carl"),
                board.entries().stream().map(LeaderboardEntry::username).toList());
        assertEquals(1, board.me().rank());
        assertEquals("Gold III", board.me().ranking().league());
        assertEquals("Silber III", board.entries().get(1).ranking().league());
        assertEquals("Bronze III", board.entries().get(3).ranking().league());
    }

    @Test
    void gleichePunkteUndSiegeGleicherRang() {
        when(users.findByRatingIsNotNull()).thenReturn(List.of(user("anna", 1000), user("bert", 1000)));
        when(results.countAllWinsByUser()).thenReturn(rows("anna", 2L, "bert", 2L));
        when(results.countAllLossesByUser()).thenReturn(List.of());

        List<LeaderboardEntry> entries = service.leaderboard(null, 10).entries();

        assertEquals(1, entries.get(0).rank());
        assertEquals(1, entries.get(1).rank());
    }
}
