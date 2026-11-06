package org.example;

import org.example.dtos.BeybladeStats;
import org.example.game.Arena;
import org.example.game.Battle;
import org.example.game.BattleOutcome;
import org.example.game.BotLauncher;
import org.example.game.FinishType;
import org.example.game.LaunchChallenge;
import org.example.game.LaunchRules;
import org.example.game.Side;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.example.BattleFixtures.ATTACK;
import static org.example.BattleFixtures.DEFENSE;
import static org.example.BattleFixtures.STAMINA;
import static org.example.BattleFixtures.fighter;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Balancing mit Anspinnen: Beide Seiten starten so gestreut wie ein durchschnittlicher Spieler (der
 * KI-Launcher dient als Stellvertreter). Es gelten dieselben Ziele wie ohne Anspinnen.
 */
class LaunchBalanceTest {

    private static final int RUNS = 4_000;

    private static BattleOutcome run(Arena arena, BeybladeStats a, BeybladeStats b, long seed) {
        Random rng = new Random(seed * 31 + 7);
        LaunchChallenge c = LaunchChallenge.random(rng);
        return new Battle(arena, fighter("a", a), fighter("b", b), seed,
                c.score(BotLauncher.input(c, rng, LaunchRules.BOT_SKILL)),
                c.score(BotLauncher.input(c, rng, LaunchRules.BOT_SKILL))).run();
    }

    private static double winRate(Arena arena, BeybladeStats a, BeybladeStats b) {
        int wins = 0;
        for (int seed = 0; seed < RUNS; seed++) {
            if (run(arena, a, b, seed).winnerSide() == Side.A) wins++;
        }
        return wins / (double) RUNS;
    }

    @Test
    void vorteilTypGewinntWeiterhinSechzigBisZweiundsiebzigProzent() {
        BeybladeStats[][] pairs = {{DEFENSE, ATTACK}, {STAMINA, DEFENSE}, {ATTACK, STAMINA}};
        for (Arena arena : Arena.all()) {
            for (BeybladeStats[] p : pairs) {
                double asA = winRate(arena, p[0], p[1]);
                double asB = 1 - winRate(arena, p[1], p[0]);
                for (double rate : new double[] {asA, asB}) {
                    assertTrue(rate >= 0.60 && rate <= 0.72, arena.name() + ": " + rate);
                }
            }
        }
    }

    @Test
    void mirrorBleibtAusgeglichen() {
        for (Arena arena : Arena.all()) {
            for (BeybladeStats s : List.of(ATTACK, DEFENSE, STAMINA)) {
                double rate = winRate(arena, s, s);
                assertTrue(rate >= 0.45 && rate <= 0.55, "Mirror (" + arena.name() + "): " + rate);
            }
        }
    }

    @Test
    void finishMixBleibtGesund() {
        for (Arena arena : Arena.all()) {
            Map<FinishType, Integer> counts = new EnumMap<>(FinishType.class);
            int total = 0;
            List<BeybladeStats> all = List.of(ATTACK, DEFENSE, STAMINA);
            for (BeybladeStats a : all) {
                for (BeybladeStats b : all) {
                    for (int seed = 0; seed < RUNS / 4; seed++) {
                        BattleOutcome o = run(arena, a, b, seed);
                        counts.merge(o.finish(), 1, Integer::sum);
                        assertTrue(o.durationMs() >= 12_000 && o.durationMs() <= 35_000);
                        total++;
                    }
                }
            }
            for (FinishType type : FinishType.values()) {
                double share = counts.getOrDefault(type, 0) / (double) total;
                assertTrue(share > 0.02 && share <= 0.55, type + " (" + arena.name() + "): " + share);
            }
        }
    }
}
