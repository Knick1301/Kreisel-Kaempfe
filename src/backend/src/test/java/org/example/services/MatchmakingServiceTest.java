package org.example.services;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** LP-Fenster der Spielersuche: wächst mit der Wartezeit, später passt jeder. */
class MatchmakingServiceTest {

    @Test
    void fensterWaechstMitDerWartezeit() {
        assertEquals(MatchmakingService.WINDOW_START, MatchmakingService.window(Duration.ZERO));
        assertEquals(MatchmakingService.WINDOW_START + 10 * MatchmakingService.WINDOW_PER_SECOND,
                MatchmakingService.window(Duration.ofSeconds(10)));
        assertTrue(MatchmakingService.window(Duration.ofSeconds(20))
                > MatchmakingService.window(Duration.ofSeconds(5)));
        assertEquals(Integer.MAX_VALUE, MatchmakingService.window(MatchmakingService.WIDE_AFTER));
    }
}
