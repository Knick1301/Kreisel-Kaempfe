package org.example.dtos;

import org.example.entities.BattleResult;
import org.example.game.FinishType;

import java.time.Instant;

public record BattleReportDto(Long id, String winner, String loser, String winnerBeyblade,
                              String loserBeyblade, FinishType finish, int rounds, Instant playedAt) {

    public static BattleReportDto from(BattleResult r) {
        return new BattleReportDto(r.getId(), r.getWinner().getUsername(), r.getLoser().getUsername(),
                r.getWinnerBeyblade(), r.getLoserBeyblade(), r.getFinish(), r.getRounds(), r.getPlayedAt());
    }
}
