package org.example;

import org.example.dtos.BeybladeStats;
import org.example.game.Archetype;
import org.example.game.BattleTuning;
import org.example.game.BotFactory;
import org.example.game.StyleVector;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotFactoryTest {

    /** Echte Teile-Kombinationen aus parts/*.json (Wheel + Track + Tip). */
    private static final List<BeybladeStats> PLAYERS = List.of(
            new BeybladeStats(7, 8, 6, 8, 79, 2),    // Sturmklinge, Schwerring, Flach
            new BeybladeStats(1, 7, 16, 2, 77, 4),   // Bollwerk, Orbit, Nadel
            new BeybladeStats(1, 12, 8, 5, 86, 4),   // Bollwerk, Schwerring, Kugel
            new BeybladeStats(3, 8, 10, 5, 77, 4),   // Wirbel, Kern, Kugel
            new BeybladeStats(7, 3, 11, 8, 72, 2),   // Sturmklinge, Orbit, Flach
            BattleFixtures.ATTACK, BattleFixtures.DEFENSE, BattleFixtures.STAMINA);

    @Test
    void budgetUndWertebereicheStimmen() {
        Random rng = new Random(1);
        for (BeybladeStats player : PLAYERS) {
            double budget = StyleVector.of(player).budget();
            for (int i = 0; i < 500; i++) {
                BotFactory.Bot bot = BotFactory.create(player, rng);
                BeybladeStats s = bot.stats();
                double botBudget = StyleVector.of(s).budget();
                assertTrue(Math.abs(botBudget / budget - 1) <= 0.10,
                        "Budget " + botBudget + " statt " + budget + " für " + s);
                assertInRange(s.attack(), BattleTuning.ATTACK_MIN, BattleTuning.ATTACK_REF);
                assertInRange(s.defense(), BattleTuning.DEFENSE_MIN, BattleTuning.DEFENSE_REF);
                assertInRange(s.stamina(), BattleTuning.STAMINA_MIN, BattleTuning.STAMINA_REF);
                assertInRange(s.dash(), BattleTuning.DASH_MIN, BattleTuning.DASH_REF);
                assertInRange(s.weight(), BattleTuning.WEIGHT_MIN, BattleTuning.WEIGHT_MAX);
                assertInRange(s.burstResistance(), BattleTuning.BURST_MIN, BattleTuning.BURST_REF);
            }
        }
    }

    @Test
    void alleArchetypenKommenVor() {
        Random rng = new Random(2);
        Set<Archetype> seen = EnumSet.noneOf(Archetype.class);
        for (int i = 0; i < 400; i++) {
            seen.add(StyleVector.of(BotFactory.create(PLAYERS.get(3), rng).stats()).archetype());
        }
        assertEquals(EnumSet.allOf(Archetype.class), seen);
    }

    @Test
    void namenSindDeutschUndKurz() {
        Random rng = new Random(3);
        for (int i = 0; i < 300; i++) {
            BotFactory.Bot bot = BotFactory.create(PLAYERS.getFirst(), rng);
            String[] words = bot.name().split(" ");
            assertEquals(2, words.length, bot.name());
            assertTrue(words[0].endsWith("er"), bot.name());
            assertTrue(bot.name().length() <= 24, bot.name());
            assertTrue(bot.accent().matches("#[0-9a-f]{6}"));
        }
    }

    @Test
    void gleicherZufallErgibtGleichenGegner() {
        assertEquals(BotFactory.create(PLAYERS.getFirst(), new Random(9)),
                BotFactory.create(PLAYERS.getFirst(), new Random(9)));
    }

    private static void assertInRange(int value, int min, int max) {
        assertTrue(value >= min && value <= max, value + " nicht in [" + min + ", " + max + "]");
    }
}
