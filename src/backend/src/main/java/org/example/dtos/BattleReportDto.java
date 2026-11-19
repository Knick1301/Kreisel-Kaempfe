package org.example.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.example.entities.BattleResult;
import org.example.game.BattleMode;
import org.example.game.FinishType;
import org.example.game.Side;

import java.time.Instant;
import java.util.List;

/**
 * Kampfergebnis. In Listen (Profil, Lobby) nur die Zusammenfassung, bei einzelnen Kämpfen zusätzlich
 * Kämpfer, Matchup und die Timeline für die Animation. Fehlende Teile werden im JSON weggelassen.
 *
 * @param rounds   nur noch für Kämpfe aus der alten Rundensimulation
 * @param timeline {@code null}, wenn sich der Kampf nicht exakt nachspielen lässt (Altbestand, andere Simulationsversion)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BattleReportDto(Long id, String winner, String loser, String winnerBeyblade,
                              String loserBeyblade, FinishType finish, int rounds, Instant playedAt,
                              BattleMode mode, Long seed, Integer durationMs, Side winnerSide, ArenaDto arena,
                              List<FighterDto> fighters, MatchupDto matchup, TimelineDto timeline,
                              Integer ratingDeltaWinner, Integer ratingDeltaLoser) {

    public static BattleReportDto from(BattleResult r) {
        return new BattleReportDto(r.getId(), r.getWinner().getUsername(), r.getLoser().getUsername(),
                r.getWinnerBeyblade(), r.getLoserBeyblade(), r.getFinish(), r.getRounds(), r.getPlayedAt(),
                mode(r), null, r.getDurationMs(), r.getWinnerSide(), null, null, null, null,
                r.getRatingDeltaWinner(), r.getRatingDeltaLoser());
    }

    public static BattleReportDto full(BattleResult r, ArenaDto arena, List<FighterDto> fighters,
                                       MatchupDto matchup, TimelineDto timeline) {
        return new BattleReportDto(r.getId(), r.getWinner().getUsername(), r.getLoser().getUsername(),
                r.getWinnerBeyblade(), r.getLoserBeyblade(), r.getFinish(), r.getRounds(), r.getPlayedAt(),
                mode(r), r.getSeed(), r.getDurationMs(), r.getWinnerSide(), arena, fighters, matchup, timeline,
                r.getRatingDeltaWinner(), r.getRatingDeltaLoser());
    }

    private static BattleMode mode(BattleResult r) {
        return r.getMode() == null ? BattleMode.PVP : r.getMode();
    }
}
