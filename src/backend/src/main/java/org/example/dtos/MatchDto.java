package org.example.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.example.game.BattleMode;
import org.example.game.MatchStatus;
import org.example.game.Side;

import java.util.List;

/**
 * Stand eines Matches aus Sicht eines Teilnehmers. Vor dem Kampf enthält es Kämpfer, Matchup und die
 * Vorgaben fürs Anspinnen, aber nie den Start des Gegners.
 *
 * @param yourSide      Seite des abfragenden Spielers
 * @param youLaunched   eigener Start schon abgegeben
 * @param opponentReady Gegner hat schon angespinnt (beim KI-Gegner sofort)
 * @param expiresInMs   verbleibende Zeit fürs Anspinnen, danach zählt ein fehlender Start als verpasst
 * @param battleId      gespeicherter Kampf, sobald beide angespinnt haben
 * @param report        vollständiger Kampfbericht inklusive Timeline, sobald beide angespinnt haben
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MatchDto(String id, BattleMode mode, MatchStatus status, Side yourSide, ArenaDto arena,
                       List<FighterDto> fighters, MatchupDto matchup, LaunchChallengeDto launch,
                       boolean youLaunched, boolean opponentReady, long expiresInMs, Long battleId,
                       BattleReportDto report) {}
