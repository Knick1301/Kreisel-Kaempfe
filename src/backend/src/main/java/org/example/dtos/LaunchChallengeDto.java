package org.example.dtos;

import org.example.game.LaunchChallenge;
import org.example.game.LaunchRules;

import java.util.List;

/**
 * Vorgaben und Regeln des Schwungmessers, damit der Client Zeiger und Zonen genau so zeigt, wie der Server
 * sie auswertet. Gewertet wird nur auf dem Server. Zeigerstand {@code p(t) = ½ − ½·cos(2π·t/periodMs)}.
 *
 * @param zones Mitten der drei Zonen auf dem Bogen (0 = links, 1 = rechts)
 */
public record LaunchChallengeDto(int windowMs, int maxTaps, int periodMs, List<Double> zones, double zoneHalf,
                                 double coreHalf, double edgeScore, double perfectQuality) {

    public static LaunchChallengeDto from(LaunchChallenge c) {
        return new LaunchChallengeDto(LaunchRules.WINDOW_MS, LaunchRules.MAX_TAPS, c.periodMs(), c.zones(),
                LaunchRules.ZONE_HALF, LaunchRules.CORE_HALF, LaunchRules.EDGE_SCORE, LaunchRules.PERFECT_QUALITY);
    }
}
