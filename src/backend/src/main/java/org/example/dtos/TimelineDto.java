package org.example.dtos;

import org.example.game.BattleTimeline;
import org.example.game.Side;
import org.example.game.TimelineEvent;

import java.util.List;
import java.util.Map;

/**
 * Timeline im API-Format: {@code energy} und {@code hits} sind nach Seite ("A"/"B") geschlüsselt.
 */
public record TimelineDto(int sampleMs, Map<Side, List<Double>> energy, Map<Side, Integer> hits,
                          List<TimelineEvent> events) {

    public static TimelineDto from(BattleTimeline t) {
        return new TimelineDto(t.sampleMs(),
                Map.of(Side.A, t.energyA(), Side.B, t.energyB()),
                Map.of(Side.A, t.hitsA(), Side.B, t.hitsB()),
                t.events());
    }
}
