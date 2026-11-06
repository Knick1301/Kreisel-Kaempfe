package org.example.dtos;

import org.example.game.BattleTuning;
import org.example.game.Side;
import org.example.game.StyleVector;

/**
 * Stil-Vorteil von A gegenüber B (positiv = A im Vorteil).
 *
 * @param favored {@code null}, wenn das Matchup ausgeglichen ist
 */
public record MatchupDto(double edge, Side favored) {

    public static MatchupDto of(StyleVector a, StyleVector b) {
        double edge = a.edgeOver(b);
        Side favored = Math.abs(edge) < BattleTuning.EVEN_EDGE ? null : (edge > 0 ? Side.A : Side.B);
        return new MatchupDto(Math.round(edge * 1000) / 1000.0, favored);
    }
}
