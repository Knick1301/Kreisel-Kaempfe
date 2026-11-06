package org.example.game;

import org.example.dtos.BeybladeStats;

/**
 * Stil eines Kreisels als Anteile von Angriff, Verteidigung und Ausdauer (Summe 1), jeweils auf den
 * erreichbaren Maximalwert normalisiert. {@code budget} ist die Summe der normalisierten Werte.
 */
public record StyleVector(double attack, double defense, double stamina, double budget) {

    public static StyleVector of(BeybladeStats stats) {
        double a = BattleTuning.norm(stats.attack(), BattleTuning.ATTACK_REF);
        double d = BattleTuning.norm(stats.defense(), BattleTuning.DEFENSE_REF);
        double s = BattleTuning.norm(stats.stamina(), BattleTuning.STAMINA_REF);
        double sum = a + d + s;
        if (sum <= 0) {
            return new StyleVector(1 / 3.0, 1 / 3.0, 1 / 3.0, 0);
        }
        return new StyleVector(a / sum, d / sum, s / sum, sum);
    }

    /** Größter Anteil, wenn er deutlich genug ist, sonst BALANCE. */
    public Archetype archetype() {
        double max = Math.max(attack, Math.max(defense, stamina));
        if (max < BattleTuning.ARCHETYPE_SHARE) return Archetype.BALANCE;
        if (max == attack) return Archetype.ATTACK;
        if (max == defense) return Archetype.DEFENSE;
        return Archetype.STAMINA;
    }

    /**
     * Matchup-Vorteil gegenüber {@code other} als σᵀ·M·σ', M mit „Verteidigung schlägt Angriff,
     * Ausdauer schlägt Verteidigung, Angriff schlägt Ausdauer“:
     * <pre>
     *        A   D   S
     *   A [  0  -1  +1 ]
     *   D [ +1   0  -1 ]
     *   S [ -1  +1   0 ]
     * </pre>
     * Antisymmetrisch: Mirror ergibt 0, ein ausgeglichener Stil gegen jeden Gegner ebenfalls 0.
     */
    public double edgeOver(StyleVector other) {
        return attack * (other.stamina - other.defense)
                + defense * (other.attack - other.stamina)
                + stamina * (other.defense - other.attack);
    }
}
