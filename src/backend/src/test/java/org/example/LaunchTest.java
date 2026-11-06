package org.example;

import org.example.dtos.BeybladeStats;
import org.example.game.Arena;
import org.example.game.Battle;
import org.example.game.BattleOutcome;
import org.example.game.BotLauncher;
import org.example.game.Launch;
import org.example.game.LaunchChallenge;
import org.example.game.LaunchInput;
import org.example.game.LaunchRules;
import org.example.game.Side;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.example.BattleFixtures.ATTACK;
import static org.example.BattleFixtures.DEFENSE;
import static org.example.BattleFixtures.STAMINA;
import static org.example.BattleFixtures.fighter;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Auswertung des Schwungmessers und sein Einfluss auf die Simulation. */
class LaunchTest {

    private static final LaunchChallenge C = new LaunchChallenge(1_400, 0.2, 0.5, 0.8);

    /** Erster Zeitpunkt, zu dem der Zeiger auf Stand {@code p} ist (Hinweg). */
    private static int timeAt(double p) {
        return (int) Math.round(C.periodMs() / (2 * Math.PI) * Math.acos(1 - 2 * p));
    }

    @Test
    void zeigerPendeltAnDenEndenLangsamInDerMitteSchnell() {
        assertEquals(0, C.needleAt(0), 1e-9);
        assertEquals(1, C.needleAt(C.periodMs() / 2), 1e-9);
        assertEquals(0, C.needleAt(C.periodMs()), 1e-9);
        double nearEnd = C.needleAt(50) - C.needleAt(0);
        double middle = C.needleAt(C.periodMs() / 4 + 25) - C.needleAt(C.periodMs() / 4 - 25);
        assertTrue(middle > 5 * nearEnd);
    }

    @Test
    void kernGibtVolleRandHalbeDanebenKeinePunkte() {
        assertEquals(1, LaunchChallenge.zoneScore(0));
        assertEquals(1, LaunchChallenge.zoneScore(LaunchRules.CORE_HALF));
        assertEquals(LaunchRules.EDGE_SCORE, LaunchChallenge.zoneScore(LaunchRules.ZONE_HALF), 1e-9);
        assertEquals(0, LaunchChallenge.zoneScore(LaunchRules.ZONE_HALF + 0.001));
        double mid = LaunchChallenge.zoneScore((LaunchRules.CORE_HALF + LaunchRules.ZONE_HALF) / 2);
        assertTrue(mid > LaunchRules.EDGE_SCORE && mid < 1);
    }

    @Test
    void alleDreiKerneSindPerfekt() {
        Launch l = C.score(new LaunchInput(List.of(timeAt(0.2), timeAt(0.5), timeAt(0.8))));
        assertEquals(3, l.hits());
        assertEquals(3, l.cores());
        assertEquals(1, l.quality());
        assertTrue(l.perfect());
        assertEquals(-1 + LaunchRules.FLY_MIN + LaunchRules.FLY_RANGE, l.landingX(), 1e-3);
    }

    @Test
    void abstufungenNachGetroffenenZonen() {
        Launch two = C.score(new LaunchInput(List.of(timeAt(0.2), timeAt(0.5), timeAt(0.65))));
        assertEquals(2, two.hits());
        assertEquals(2 / 3.0, two.quality(), 1e-3);
        Launch edge = C.score(new LaunchInput(List.of(timeAt(0.2 + LaunchRules.ZONE_HALF), timeAt(0.5))));
        assertEquals(2, edge.hits());
        assertEquals(1, edge.cores());
        assertEquals((LaunchRules.EDGE_SCORE + 1) / 3, edge.quality(), 0.01);
        assertTrue(edge.quality() < two.quality());
        // weiter fliegt nur, wer besser trifft
        assertTrue(edge.landingX() < two.landingX());
    }

    @Test
    void zweimalDieselbeZoneZaehltNurEinmal() {
        int t = timeAt(0.5);
        Launch l = C.score(new LaunchInput(List.of(t, C.periodMs() - t, C.periodMs() + t)));
        assertEquals(1, l.hits());
        assertEquals(1 / 3.0, l.quality(), 1e-3);
    }

    @Test
    void daneben() {
        Launch l = C.score(new LaunchInput(List.of(timeAt(0.35), timeAt(0.65), timeAt(0.95))));
        assertEquals(0, l.hits());
        assertTrue(l.missed());
        assertEquals(0, l.quality());
    }

    @Test
    void unplausibleOderFehlendeEingabenZaehlenAlsVerpasst() {
        assertSame(Launch.MISSED, C.score(null));
        assertSame(Launch.MISSED, C.score(LaunchInput.NONE));
        assertSame(Launch.MISSED, C.score(new LaunchInput(null)));
        assertSame(Launch.MISSED, C.score(new LaunchInput(List.of(500, 300))));
        assertSame(Launch.MISSED, C.score(new LaunchInput(List.of(-5))));
        assertSame(Launch.MISSED, C.score(new LaunchInput(List.of(LaunchRules.WINDOW_MS + 1))));
        assertSame(Launch.MISSED, C.score(new LaunchInput(List.of(100, 200, 300, 400))));
        assertTrue(Launch.MISSED.missed());
    }

    @Test
    void ohneAnspinnenRechnetDieSimulationWieVersionEins() {
        for (long seed = 0; seed < 50; seed++) {
            BattleOutcome old = new Battle(Arena.standard(), fighter("a", ATTACK), fighter("b", DEFENSE), seed).run();
            BattleOutcome now = new Battle(Arena.standard(), fighter("a", ATTACK), fighter("b", DEFENSE), seed,
                    null, null).run();
            assertEquals(old, now);
        }
    }

    @Test
    void gleicherSeedUndGleicheStartsErgebenGleicheTimeline() {
        Launch la = new Launch(0.83, 3, 1, -0.5, 0.0);
        Launch lb = new Launch(0.41, 2, 0, -0.6, 0.0);
        BattleOutcome first = new Battle(Arena.KRATER, fighter("a", STAMINA), fighter("b", ATTACK), 77, la, lb).run();
        BattleOutcome second = new Battle(Arena.KRATER, fighter("a", STAMINA), fighter("b", ATTACK), 77, la, lb).run();
        assertEquals(first, second);
    }

    @Test
    void besseresAnspinnenHilftAberEntscheidetNichtAllein() {
        for (BeybladeStats s : new BeybladeStats[] {ATTACK, DEFENSE, STAMINA}) {
            double good = winRate(s, s, new Launch(1, 3, 3, null, null), new Launch(0.35, 1, 0, null, null));
            double missed = winRate(s, s, new Launch(1, 3, 3, null, null), Launch.MISSED);
            assertTrue(good > 0.57 && good < 0.70, "perfekt gegen schwach: " + good);
            assertTrue(missed > good && missed < 0.76, "perfekt gegen verpasst: " + missed);
        }
        // Der Matchup-Vorteil bleibt auch mit schwachem Start gegen perfekten Gegner eher erhalten
        double favoredButWeak = winRate(DEFENSE, ATTACK, new Launch(0.35, 1, 0, null, null), new Launch(1, 3, 3, null, null));
        assertTrue(favoredButWeak > 0.5, "Verteidigung mit schwachem Start gegen Angriff: " + favoredButWeak);
    }

    @Test
    void alleDreiZonenSindSehrSchwer() {
        Random rng = new Random(3);
        double sum = 0;
        int all = 0;
        int perfect = 0;
        int n = 10_000;
        for (int i = 0; i < n; i++) {
            LaunchChallenge c = LaunchChallenge.random(rng);
            Launch l = c.score(BotLauncher.input(c, rng, LaunchRules.BOT_SKILL));
            sum += l.quality();
            if (l.hits() == 3) all++;
            if (l.perfect()) perfect++;
        }
        double avg = sum / n;
        assertTrue(avg > 0.2 && avg < 0.5, "Mittlere Qualität der KI: " + avg);
        assertTrue(all / (double) n < 0.1, "Anteil 3/3: " + all / (double) n);
        assertTrue(perfect / (double) n < 0.01, "Anteil perfekt: " + perfect / (double) n);
    }

    @Test
    void vorgabenBleibenImRegelrahmen() {
        Random rng = new Random(5);
        for (int i = 0; i < 1_000; i++) {
            LaunchChallenge c = LaunchChallenge.random(rng);
            assertTrue(c.periodMs() >= LaunchRules.PERIOD_MIN_MS && c.periodMs() <= LaunchRules.PERIOD_MAX_MS);
            List<Double> z = c.zones();
            assertTrue(z.get(0) >= LaunchRules.ZONE_MIN && z.get(2) <= LaunchRules.ZONE_MAX);
            assertTrue(z.get(1) - z.get(0) >= LaunchRules.ZONE_GAP - 1e-3);
            assertTrue(z.get(2) - z.get(1) >= LaunchRules.ZONE_GAP - 1e-3);
        }
    }

    private static double winRate(BeybladeStats a, BeybladeStats b, Launch la, Launch lb) {
        int runs = 3_000;
        int wins = 0;
        for (int seed = 0; seed < runs; seed++) {
            if (new Battle(Arena.standard(), fighter("a", a), fighter("b", b), seed, la, lb).run().winnerSide()
                    == Side.A) wins++;
        }
        return wins / (double) runs;
    }
}
