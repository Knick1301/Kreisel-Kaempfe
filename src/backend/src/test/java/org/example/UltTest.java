package org.example;

import org.example.dtos.BeybladeStats;
import org.example.game.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.example.BattleFixtures.ATTACK;
import static org.example.BattleFixtures.DEFENSE;
import static org.example.BattleFixtures.STAMINA;
import static org.example.BattleFixtures.fighter;
import static org.junit.jupiter.api.Assertions.*;

/** Ults: Auslösen, Wirkung, Abhängigkeit vom Anspinnen und Replay alter Kämpfe. */
class UltTest {

    private static final Launch PERFECT = new Launch(1, 3, 3, null, null);

    private static BattleOutcome run(BeybladeStats a, BeybladeStats b, long seed) {
        return new Battle(Arena.standard(), fighter("a", a), fighter("b", b), seed).run();
    }

    private static List<TimelineEvent> ults(BattleOutcome o) {
        return o.timeline().events().stream().filter(e -> e.type() == EventType.ULT).toList();
    }

    @Test
    void ultPasstOhneAngabeZumStil() {
        assertEquals(UltType.SMASH, fighter("a", ATTACK).ult());
        assertEquals(UltType.SHIELD, fighter("a", DEFENSE).ult());
        assertEquals(UltType.SURGE, fighter("a", STAMINA).ult());
        assertEquals(UltType.RUSH, new Fighter("a", "x", ATTACK, UltType.RUSH).ult());
    }

    @Test
    void ultsLoesenAusAberNichtVorDemAngriff() {
        int total = 0;
        for (long seed = 0; seed < 200; seed++) {
            BattleOutcome o = run(ATTACK, DEFENSE, seed);
            for (TimelineEvent e : ults(o)) {
                assertTrue(e.t() >= BattleTuning.ENGAGE_MS, "Ult zu früh: " + e.t());
                assertNotNull(e.side());
                assertNotNull(e.ult());
                total++;
            }
            assertEquals(o.timeline().energyA().size(), o.timeline().ultA().size());
        }
        // grob eine Ult pro Kreisel und Kampf
        double perFight = total / 200.0;
        assertTrue(perFight > 1 && perFight < 4, "Ults pro Kampf: " + perFight);
    }

    @Test
    void vernichtungsschlagTrifftNachDemAusholenUndPralltNieAb() {
        int strikes = 0;
        for (long seed = 0; seed < 200; seed++) {
            List<TimelineEvent> ev = run(ATTACK, DEFENSE, seed).timeline().events();
            for (int i = 0; i < ev.size(); i++) {
                TimelineEvent e = ev.get(i);
                if (e.type() != EventType.CLASH || e.ult() != UltType.SMASH) continue;
                strikes++;
                int at = e.t();
                assertTrue(ev.stream().anyMatch(u -> u.type() == EventType.ULT && u.ult() == UltType.SMASH
                        && u.t() == at - BattleTuning.SMASH_WINDUP_MS), "Schlag ohne Ausholen bei " + at);
                boolean deflected = i + 1 < ev.size() && ev.get(i + 1).type() == EventType.DEFLECT
                        && ev.get(i + 1).t() == at;
                assertFalse(deflected, "Vernichtungsschlag abgewehrt bei " + at);
            }
        }
        assertTrue(strikes > 0);
    }

    @Test
    void zeitlicheUltsEndenMitUltEnd() {
        for (long seed = 0; seed < 100; seed++) {
            BattleOutcome o = run(DEFENSE, STAMINA, seed);
            for (TimelineEvent e : ults(o)) {
                if (e.ult() == UltType.SMASH) continue;
                assertNotNull(e.durationMs());
                int end = e.t() + e.durationMs();
                if (end < o.durationMs()) {
                    assertTrue(o.timeline().events().stream().anyMatch(x -> x.type() == EventType.ULT_END
                            && x.side() == e.side() && x.t() <= end), "Kein Ende für Ult bei " + e.t());
                }
            }
        }
    }

    @Test
    void gutesAnspinnenMachtDieUltStaerker() {
        BattleOutcome o = new Battle(Arena.standard(), fighter("a", DEFENSE), fighter("b", DEFENSE), 3,
                PERFECT, Launch.MISSED).run();
        double perfect = 0;
        double missed = 0;
        for (TimelineEvent e : ults(o)) {
            if (e.side() == Side.A) perfect = e.intensity();
            else missed = e.intensity();
        }
        if (perfect > 0) assertEquals(BattleTuning.ULT_POWER_BASE + BattleTuning.ULT_POWER_LAUNCH
                + BattleTuning.ULT_POWER_PERFECT, perfect, 1e-3);
        if (missed > 0) assertEquals(BattleTuning.ULT_POWER_BASE, missed, 1e-3);
        // Vorgeladene Leiste
        assertEquals(BattleTuning.ULT_LAUNCH_START, o.timeline().ultA().get(0), 0.01);
        assertEquals(0, o.timeline().ultB().get(0), 1e-9);
    }

    @Test
    void ohneUltsRechnetDieSimulationWieVersionZwei() {
        for (long seed = 0; seed < 50; seed++) {
            BattleOutcome o = new Battle(Arena.standard(), fighter("a", ATTACK), fighter("b", STAMINA), seed,
                    null, null, 2).run();
            assertTrue(ults(o).isEmpty());
            assertTrue(o.timeline().ultA().isEmpty());
        }
    }

    @Test
    void zugrichtungLenktNurDenLandepunkt() {
        LaunchChallenge c = new LaunchChallenge(1300, 0.2, 0.5, 0.8);
        // Loslassen genau über der mittleren Zone (Zeiger bei 0,5 nach einer Viertelperiode)
        List<Integer> taps = List.of(c.periodMs() / 4);
        Launch straight = c.score(new LaunchInput(taps, null, 1.0, 1.0));
        Launch right = c.score(new LaunchInput(taps, 1.0, 1.0, 1.0));
        Launch wild = c.score(new LaunchInput(taps, -7.0, 1.0, 1.0));
        assertEquals(straight.quality(), right.quality());
        assertEquals(0.0, straight.landingY());
        assertEquals(LaunchRules.AIM_RANGE, right.landingY(), 1e-9);
        assertEquals(-LaunchRules.AIM_RANGE, wild.landingY(), 1e-9);
    }
}
