package org.example;

import org.example.dtos.BeybladeStats;
import org.example.game.Fighter;

/** Reine Archetypen mit (nahezu) gleichem Budget, alle Werte im Bereich der Teile. */
final class BattleFixtures {

    private BattleFixtures() {}

    /** Budget 1,625 (normalisiert): viel Angriff und Dash, leicht, wenig Burst-Resistenz. */
    static final BeybladeStats ATTACK = new BeybladeStats(7, 3, 6, 8, 75, 2);
    /** Budget 1,643: viel Verteidigung, schwer, hohe Burst-Resistenz, kaum Dash. */
    static final BeybladeStats DEFENSE = new BeybladeStats(1, 12, 8, 2, 86, 4);
    /** Budget 1,619: viel Ausdauer, leicht. */
    static final BeybladeStats STAMINA = new BeybladeStats(2, 4, 16, 3, 73, 3);

    static Fighter fighter(String name, BeybladeStats stats) {
        return new Fighter(name, name, stats);
    }
}
