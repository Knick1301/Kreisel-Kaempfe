package org.example.game;

import java.util.List;

/**
 * Kompakter Kampfverlauf für die Animation: Energie-Samples im festen Raster plus Ereignisse.
 * Positionen enthält die Timeline bewusst nicht, die erzeugt der Choreograf im Frontend.
 *
 * @param sampleMs Abstand der Energie-Samples in ms (Sample i gehört zu i * sampleMs)
 * @param energyA  Spin-Energie von A, normalisiert 0–1
 * @param energyB  Spin-Energie von B, normalisiert 0–1
 * @param hitsA    Volltreffer (nicht abgewehrte Clashes) von A
 * @param hitsB    Volltreffer von B
 * @param ultA     Ult-Leiste von A (0–1) im selben Raster, leer bei Kämpfen ohne Ults
 * @param ultB     Ult-Leiste von B
 */
public record BattleTimeline(int sampleMs, List<Double> energyA, List<Double> energyB,
                             List<TimelineEvent> events, int hitsA, int hitsB,
                             List<Double> ultA, List<Double> ultB) {

    public List<Double> energy(Side side) {
        return side == Side.A ? energyA : energyB;
    }

    public int hits(Side side) {
        return side == Side.A ? hitsA : hitsB;
    }

    public List<Double> ult(Side side) {
        return side == Side.A ? ultA : ultB;
    }
}
