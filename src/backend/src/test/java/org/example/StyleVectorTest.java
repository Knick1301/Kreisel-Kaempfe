package org.example;

import org.example.dtos.BeybladeStats;
import org.example.game.Archetype;
import org.example.game.StyleVector;
import org.junit.jupiter.api.Test;

import static org.example.BattleFixtures.ATTACK;
import static org.example.BattleFixtures.DEFENSE;
import static org.example.BattleFixtures.STAMINA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StyleVectorTest {

    private static StyleVector of(BeybladeStats s) {
        return StyleVector.of(s);
    }

    @Test
    void archetypenWerdenErkannt() {
        assertEquals(Archetype.ATTACK, of(ATTACK).archetype());
        assertEquals(Archetype.DEFENSE, of(DEFENSE).archetype());
        assertEquals(Archetype.STAMINA, of(STAMINA).archetype());
        // 3/7, 5/12, 7/16 → fast gleiche Anteile
        assertEquals(Archetype.BALANCE, of(new BeybladeStats(3, 5, 7, 5, 78, 3)).archetype());
    }

    @Test
    void anteileSummierenSichAufEins() {
        StyleVector v = of(ATTACK);
        assertEquals(1.0, v.attack() + v.defense() + v.stamina(), 1e-9);
        assertEquals(7 / 7.0 + 3 / 12.0 + 6 / 16.0, v.budget(), 1e-9);
    }

    @Test
    void matchupIstSchereSteinPapier() {
        assertTrue(of(DEFENSE).edgeOver(of(ATTACK)) > 0.1);
        assertTrue(of(STAMINA).edgeOver(of(DEFENSE)) > 0.1);
        assertTrue(of(ATTACK).edgeOver(of(STAMINA)) > 0.1);
    }

    @Test
    void matchupIstAntisymmetrischUndMirrorNeutral() {
        StyleVector a = of(ATTACK);
        StyleVector s = of(STAMINA);
        assertEquals(-a.edgeOver(s), s.edgeOver(a), 1e-12);
        assertEquals(0, a.edgeOver(a), 1e-12);
        StyleVector even = new StyleVector(1 / 3.0, 1 / 3.0, 1 / 3.0, 1);
        assertEquals(0, even.edgeOver(a), 1e-12);
        assertEquals(0, even.edgeOver(of(DEFENSE)), 1e-12);
    }
}
