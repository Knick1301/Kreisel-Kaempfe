package org.example;

import org.example.dtos.BeybladeStats;
import org.example.game.Arena;
import org.example.game.Battle;
import org.example.game.BattleOutcome;
import org.example.game.Fighter;
import org.example.game.FinishType;
import org.example.game.Side;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.example.BattleFixtures.ATTACK;
import static org.example.BattleFixtures.DEFENSE;
import static org.example.BattleFixtures.STAMINA;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Monte-Carlo-Balancing mit festen Seeds. Schlägt fehl, sobald das Tuning das Schere-Stein-Papier-Gefüge
 * oder den Finish-Mix verlässt.
 */
class BattleBalanceTest {

    private static final int RUNS = 5_000;

    private static double winRate(Arena arena, BeybladeStats a, BeybladeStats b) {
        Fighter fa = BattleFixtures.fighter("a", a);
        Fighter fb = BattleFixtures.fighter("b", b);
        int wins = 0;
        for (int seed = 0; seed < RUNS; seed++) {
            if (new Battle(arena, fa, fb, seed).run().winnerSide() == Side.A) wins++;
        }
        return wins / (double) RUNS;
    }

    /** Vorteil-Typ gewinnt 60–72 %, egal auf welcher Seite er steht, in jeder Arena. */
    private static void assertAdvantage(String label, BeybladeStats favored, BeybladeStats other) {
        for (Arena arena : Arena.all()) {
            double asA = winRate(arena, favored, other);
            double asB = 1 - winRate(arena, other, favored);
            for (double rate : new double[]{asA, asB}) {
                assertTrue(rate >= 0.60 && rate <= 0.72,
                        label + " (" + arena.name() + "): " + Math.round(rate * 1000) / 10.0 + " %");
                assertTrue(rate < 0.80);
            }
        }
    }

    @Test
    void verteidigungSchlaegtEherAngriff() {
        assertAdvantage("Verteidigung vs Angriff", DEFENSE, ATTACK);
    }

    @Test
    void ausdauerSchlaegtEherVerteidigung() {
        assertAdvantage("Ausdauer vs Verteidigung", STAMINA, DEFENSE);
    }

    @Test
    void angriffSchlaegtEherAusdauer() {
        assertAdvantage("Angriff vs Ausdauer", ATTACK, STAMINA);
    }

    @Test
    void mirrorMatchupsSindAusgeglichen() {
        for (Arena arena : Arena.all()) {
            for (BeybladeStats s : List.of(ATTACK, DEFENSE, STAMINA)) {
                double rate = winRate(arena, s, s);
                assertTrue(rate >= 0.45 && rate <= 0.55, "Mirror " + s + " (" + arena.name() + "): " + rate);
            }
        }
    }

    @Test
    void keinFinishTypDominiertUndJederKommtVor() {
        for (Arena arena : Arena.all()) assertFinishMix(arena);
    }

    private static void assertFinishMix(Arena arena) {
        Map<FinishType, Integer> counts = new EnumMap<>(FinishType.class);
        int total = 0;
        List<BeybladeStats> all = List.of(ATTACK, DEFENSE, STAMINA);
        for (BeybladeStats a : all) {
            for (BeybladeStats b : all) {
                Fighter fa = BattleFixtures.fighter("a", a);
                Fighter fb = BattleFixtures.fighter("b", b);
                for (int seed = 0; seed < RUNS; seed++) {
                    BattleOutcome o = new Battle(arena, fa, fb, seed).run();
                    counts.merge(o.finish(), 1, Integer::sum);
                    total++;
                }
            }
        }
        for (FinishType type : FinishType.values()) {
            double share = counts.getOrDefault(type, 0) / (double) total;
            assertTrue(share > 0.02, type + " kommt kaum vor (" + arena.name() + "): " + share);
            assertTrue(share <= 0.55, type + " dominiert (" + arena.name() + "): " + share);
        }
    }
}
