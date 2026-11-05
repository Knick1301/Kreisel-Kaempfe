package org.example;

import org.example.dtos.BeybladeStats;
import org.example.game.Arena;
import org.example.game.Battle;
import org.example.game.BattleOutcome;
import org.example.game.Fighter;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleTest {

    private static final Fighter STRONG =
            new Fighter("a", "Stark", new BeybladeStats(12, 12, 10, 4, 80, 6));
    private static final Fighter WEAK =
            new Fighter("b", "Schwach", new BeybladeStats(2, 2, 2, 0, 60, 0));

    @Test
    void gleicherSeedErgibtGleichesErgebnis() {
        BattleOutcome first = new Battle(Arena.standard(), STRONG, WEAK, new Random(42)).run();
        BattleOutcome second = new Battle(Arena.standard(), STRONG, WEAK, new Random(42)).run();
        assertEquals(first, second);
    }

    @Test
    void staerkererKreiselGewinntFastImmer() {
        int wins = 0;
        for (int seed = 0; seed < 200; seed++) {
            BattleOutcome o = new Battle(Arena.standard(), STRONG, WEAK, new Random(seed)).run();
            if (o.winner() == STRONG) wins++;
        }
        assertTrue(wins > 180, "Starker Kreisel gewann nur " + wins + " von 200");
    }

    @Test
    void kampfEndetInnerhalbDesRundenlimits() {
        Fighter twin = new Fighter("c", "Zwilling", STRONG.stats());
        for (int seed = 0; seed < 50; seed++) {
            BattleOutcome o = new Battle(Arena.standard(), STRONG, twin, new Random(seed)).run();
            assertTrue(o.rounds() >= 1 && o.rounds() <= Arena.standard().maxRounds());
            assertTrue(o.winner() != o.loser());
        }
    }
}
