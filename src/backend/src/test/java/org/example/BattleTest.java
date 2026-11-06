package org.example;

import org.example.dtos.BeybladeStats;
import org.example.game.Arena;
import org.example.game.Battle;
import org.example.game.BattleOutcome;
import org.example.game.BattleTimeline;
import org.example.game.BattleTuning;
import org.example.game.EventType;
import org.example.game.Fighter;
import org.example.game.FinishType;
import org.example.game.Side;
import org.example.game.TimelineEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleTest {

    private static final Fighter STRONG =
            new Fighter("a", "Stark", new BeybladeStats(12, 12, 10, 4, 80, 6));
    private static final Fighter WEAK =
            new Fighter("b", "Schwach", new BeybladeStats(2, 2, 2, 0, 60, 0));

    private static final List<Fighter> ALL = List.of(
            BattleFixtures.fighter("Angriff", BattleFixtures.ATTACK),
            BattleFixtures.fighter("Verteidigung", BattleFixtures.DEFENSE),
            BattleFixtures.fighter("Ausdauer", BattleFixtures.STAMINA),
            STRONG, WEAK);

    private static BattleOutcome run(Fighter a, Fighter b, long seed) {
        return new Battle(Arena.standard(), a, b, seed).run();
    }

    @Test
    void gleicherSeedErgibtGleichesErgebnisUndGleicheTimeline() {
        Fighter atk = ALL.get(0);
        Fighter sta = ALL.get(2);
        for (long seed : new long[]{0, 42, 9_007_199_254_740_991L}) {
            assertEquals(run(atk, sta, seed), run(atk, sta, seed));
        }
        assertNotEquals(run(atk, sta, 1).timeline(), run(atk, sta, 2).timeline());
    }

    @Test
    void staerkererKreiselGewinntFastImmer() {
        int wins = 0;
        for (int seed = 0; seed < 200; seed++) {
            if (run(STRONG, WEAK, seed).winnerSide() == Side.A) wins++;
        }
        assertTrue(wins > 180, "Starker Kreisel gewann nur " + wins + " von 200");
    }

    @Test
    void kampfdauerBleibtImErlaubtenBereich() {
        for (Fighter a : ALL) {
            for (Fighter b : ALL) {
                for (int seed = 0; seed < 300; seed++) {
                    BattleOutcome o = run(a, b, seed);
                    assertTrue(o.durationMs() >= BattleTuning.MIN_KO_MS
                                    && o.durationMs() <= BattleTuning.TIME_LIMIT_MS,
                            "Dauer " + o.durationMs() + " ms bei Seed " + seed);
                    if (o.finish() == FinishType.DECISION) {
                        assertEquals(BattleTuning.TIME_LIMIT_MS, o.durationMs());
                    }
                }
            }
        }
    }

    @Test
    void timelinePasstZumErgebnis() {
        for (Fighter a : ALL) {
            for (Fighter b : ALL) {
                for (int seed = 0; seed < 100; seed++) {
                    assertConsistent(run(a, b, seed));
                }
            }
        }
    }

    @Test
    void taschenPassenZumFrontend() {
        Arena arena = Arena.standard();
        assertEquals("standard", arena.id());
        // muss zu src/frontend/src/game/arenas.ts passen
        assertEquals(List.of(Math.PI / 2, 7 * Math.PI / 6, 11 * Math.PI / 6), arena.pockets());
        assertEquals(Math.toRadians(14), arena.pocketHalfWidth(), 1e-12);
        assertEquals(arena, Arena.find("standard").orElseThrow());
        assertTrue(Arena.find("gibt-es-nicht").isEmpty());
    }

    private static void assertConsistent(BattleOutcome o) {
        BattleTimeline tl = o.timeline();
        List<TimelineEvent> events = tl.events();
        assertEquals(EventType.LAUNCH, events.getFirst().type());
        for (int i = 1; i < events.size(); i++) {
            assertTrue(events.get(i).t() >= events.get(i - 1).t(), "Events nicht sortiert");
        }

        int samples = o.durationMs() / tl.sampleMs() + 1;
        assertEquals(samples, tl.energyA().size());
        assertEquals(samples, tl.energyB().size());
        assertTrue(tl.energyA().stream().allMatch(e -> e >= 0 && e <= 1));
        assertTrue(tl.energyB().stream().allMatch(e -> e >= 0 && e <= 1));

        TimelineEvent last = events.getLast();
        assertEquals(o.durationMs(), last.t());
        Side loser = o.winnerSide().other();
        switch (o.finish()) {
            case BURST -> {
                assertEquals(EventType.BURST, last.type());
                assertEquals(loser, last.victim());
            }
            case RING_OUT -> {
                assertEquals(EventType.RING_OUT, last.type());
                assertEquals(loser, last.victim());
                double pocket = Arena.standard().pockets().get(last.pocket());
                double diff = Math.abs(Math.atan2(Math.sin(last.angle() - pocket), Math.cos(last.angle() - pocket)));
                assertTrue(diff <= Arena.standard().pocketHalfWidth(), "Ring-Out-Winkel außerhalb der Tasche");
            }
            case SPIN_OUT -> {
                assertEquals(EventType.SPIN_OUT, last.type());
                assertEquals(loser, last.victim());
            }
            case DECISION -> assertEquals(EventType.TIMEOUT, last.type());
        }

        int hitsA = 0;
        int hitsB = 0;
        for (int i = 0; i < events.size(); i++) {
            TimelineEvent e = events.get(i);
            if (e.type() != EventType.CLASH) continue;
            assertTrue(e.intensity() >= 0 && e.intensity() <= 1);
            assertTrue(e.t() >= BattleTuning.TORNADO_MS);
            boolean deflected = i + 1 < events.size() && events.get(i + 1).type() == EventType.DEFLECT
                    && events.get(i + 1).t() == e.t();
            if (!deflected) {
                if (e.attacker() == Side.A) hitsA++;
                else hitsB++;
            }
        }
        assertEquals(hitsA, tl.hitsA());
        assertEquals(hitsB, tl.hitsB());
    }
}
