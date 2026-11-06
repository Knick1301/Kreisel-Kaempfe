package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.game.BattleTuning;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.ToIntFunction;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Die Normalisierung in BattleTuning muss zu den Teilen in parts/*.json passen. */
class TuningCatalogTest {

    private static JsonNode read(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new ObjectMapper().readTree(in);
        }
    }

    private static int max(JsonNode parts, String field) {
        return stream(parts).mapToInt(p -> p.path(field).asInt(0)).max().orElse(0);
    }

    private static int min(JsonNode parts, ToIntFunction<JsonNode> f) {
        return stream(parts).mapToInt(f).min().orElse(0);
    }

    private static java.util.stream.Stream<JsonNode> stream(JsonNode parts) {
        return StreamSupport.stream(parts.spliterator(), false);
    }

    @Test
    void referenzwerteEntsprechenDenTeilen() throws IOException {
        JsonNode wheels = read("parts/fusion-wheels.json");
        JsonNode tracks = read("parts/spin-tracks.json");
        JsonNode tips = read("parts/tips.json");

        assertEquals(max(wheels, "attack"), BattleTuning.ATTACK_REF);
        assertEquals(max(wheels, "defense") + max(tracks, "defense"), BattleTuning.DEFENSE_REF);
        assertEquals(max(tracks, "stamina") + max(tips, "stamina"), BattleTuning.STAMINA_REF);
        assertEquals(max(tips, "dash"), BattleTuning.DASH_REF);
        assertEquals(max(wheels, "burstResistance"), BattleTuning.BURST_REF);

        assertEquals(min(wheels, p -> p.path("attack").asInt(0)), BattleTuning.ATTACK_MIN);
        assertEquals(min(wheels, p -> p.path("defense").asInt(0)) + min(tracks, p -> p.path("defense").asInt(0)),
                BattleTuning.DEFENSE_MIN);
        assertEquals(min(tracks, p -> p.path("stamina").asInt(0)) + min(tips, p -> p.path("stamina").asInt(0)),
                BattleTuning.STAMINA_MIN);
        assertEquals(min(tips, p -> p.path("dash").asInt(0)), BattleTuning.DASH_MIN);
        assertEquals(min(wheels, p -> p.path("burstResistance").asInt(0)), BattleTuning.BURST_MIN);

        ToIntFunction<JsonNode> weight = p -> p.get("weight").asInt();
        assertEquals(min(wheels, weight) + min(tracks, weight) + min(tips, weight), BattleTuning.WEIGHT_MIN);
        assertEquals(max(wheels, "weight") + max(tracks, "weight") + max(tips, "weight"), BattleTuning.WEIGHT_MAX);
    }
}
