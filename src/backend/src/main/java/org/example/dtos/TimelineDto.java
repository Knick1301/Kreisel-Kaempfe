package org.example.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.example.game.BattleTimeline;
import org.example.game.Side;
import org.example.game.TimelineEvent;

import java.util.List;
import java.util.Map;

/**
 * Timeline im API-Format: {@code energy}, {@code hits} und {@code ult} sind nach Seite ("A"/"B") geschlüsselt.
 * {@code ult} fehlt bei Kämpfen ohne Ults.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimelineDto(int sampleMs, Map<Side, List<Double>> energy, Map<Side, Integer> hits,
                          List<TimelineEvent> events, Map<Side, List<Double>> ult) {

    public static TimelineDto from(BattleTimeline t) {
        return new TimelineDto(t.sampleMs(),
                Map.of(Side.A, t.energyA(), Side.B, t.energyB()),
                Map.of(Side.A, t.hitsA(), Side.B, t.hitsB()),
                t.events(),
                t.ultA().isEmpty() ? null : Map.of(Side.A, t.ultA(), Side.B, t.ultB()));
    }
}
