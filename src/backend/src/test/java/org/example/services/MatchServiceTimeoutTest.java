package org.example.services;

import org.example.dtos.MatchDto;
import org.example.entities.Beyblade;
import org.example.entities.User;
import org.example.game.LaunchRules;
import org.example.game.MatchStatus;
import org.example.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Wer nicht anspinnt, blockiert das Match nicht: Nach der Frist zählt ein verpasster Start. */
@SpringBootTest
class MatchServiceTimeoutTest {

    @Autowired MatchService matches;
    @Autowired UserRepository users;

    @AfterEach
    void resetClock() {
        matches.setClock(Clock.systemUTC());
    }

    @Test
    @Transactional
    void nachAblaufDerFristWirdMitVerpasstemStartSimuliert() {
        Instant start = Instant.parse("2026-11-06T12:00:00Z");
        matches.setClock(Clock.fixed(start, ZoneOffset.UTC));
        User me = users.findByUsername("demo").orElseThrow();
        Beyblade bey = me.getBeybladeConfigurations().getFirst();

        MatchDto m = matches.createBotMatch("demo", bey.getId(), null);
        assertEquals(MatchStatus.LAUNCH, matches.get(m.id(), "demo").status());

        Instant expired = start.plusMillis(LaunchRules.SUBMIT_TIMEOUT_MS + 1);
        matches.setClock(Clock.fixed(expired, ZoneOffset.UTC));
        MatchDto fight = matches.get(m.id(), "demo");
        assertEquals(MatchStatus.FIGHT, fight.status());
        assertNotNull(fight.report());
        assertTrue(fight.report().fighters().getFirst().launch().missed());

        // Wer nach dem Anspinnen verschwindet, wird nach Kampfende plus Spielraum trotzdem gewertet
        Instant late = expired.plusMillis(fight.report().durationMs()).plus(MatchService.FIGHT_GRACE).plusMillis(1);
        matches.setClock(Clock.fixed(late, ZoneOffset.UTC));
        MatchDto done = matches.get(m.id(), "demo");
        assertEquals(MatchStatus.DONE, done.status());
        assertNotNull(done.battleId());
        assertEquals(fight.report().durationMs(), done.report().durationMs());
    }
}
