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

        ToIntFunction<JsonNode> weight = p -> p.get("weight").asInt();
        assertEquals(min(wheels, weight) + min(tracks, weight) + min(tips, weight), BattleTuning.WEIGHT_MIN);
        assertEquals(max(wheels, "weight") + max(tracks, "weight") + max(tips, "weight"), BattleTuning.WEIGHT_MAX);
    }
}
