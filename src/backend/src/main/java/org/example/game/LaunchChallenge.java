package org.example.game;

import java.util.Arrays;
import java.util.List;

import java.util.Random;

import static org.example.game.LaunchRules.*;

/**
 * Vorgaben fürs Anspinnen eines Matches. Beide Seiten bekommen dieselben Werte. Tempo des Zeigers und Lage
 * der Zonen variieren pro Match.
 *
 * @param periodMs Dauer einmal hin und zurück
 * @param zone0    Mitten der drei Zonen auf dem Bogen (0–1), aufsteigend
 */
public record LaunchChallenge(int periodMs, double zone0, double zone1, double zone2) {

    public static LaunchChallenge random(Random rng) {
        int period = PERIOD_MIN_MS + rng.nextInt(PERIOD_MAX_MS - PERIOD_MIN_MS + 1);
        double[] z = new double[ZONES];
        do {
            for (int i = 0; i < ZONES; i++) z[i] = ZONE_MIN + rng.nextDouble() * (ZONE_MAX - ZONE_MIN);
            Arrays.sort(z);
        } while (z[1] - z[0] < ZONE_GAP || z[2] - z[1] < ZONE_GAP);
        return new LaunchChallenge(period, round(z[0]), round(z[1]), round(z[2]));
    }

    public List<Double> zones() {
        return List.of(zone0, zone1, zone2);
    }

    /** Stand des Zeigers auf dem Bogen (0 = links, 1 = rechts) zur Zeit {@code tMs} ab Start. */
    public double needleAt(int tMs) {
        return 0.5 - 0.5 * StrictMath.cos(2 * Math.PI * tMs / periodMs);
    }

    /** Wert eines Taps mit Abstand {@code d} zur Zonenmitte: Kern 1, Rand 0,5, außerhalb 0. */
    public static double zoneScore(double d) {
        if (d <= CORE_HALF) return 1;
        if (d > ZONE_HALF) return 0;
        return 1 - (1 - EDGE_SCORE) * (d - CORE_HALF) / (ZONE_HALF - CORE_HALF);
    }

    /** Timing beim Loslassen zur Zeit {@code tMs}: bester Zonenwert über alle Zonen (0–1). */
    public double timingAt(int tMs) {
        double p = needleAt(tMs);
        double best = 0;
        for (double z : zones()) best = Math.max(best, zoneScore(Math.abs(p - z)));
        return best;
    }

    /**
     * Wertet das Loslassen aus: Timing mal Zugfaktor. Unplausible Eingaben (außerhalb des Zeitfensters, mehr
     * als ein Loslassen) und Loslassen neben jeder Zone zählen als verpasster Start.
     */
    public Launch score(LaunchInput input) {
        List<Integer> taps = input == null || input.taps() == null ? List.of() : input.taps();
        if (taps.size() != MAX_TAPS) return Launch.MISSED;
        Integer t = taps.getFirst();
        if (t == null || t < 0 || t > WINDOW_MS) return Launch.MISSED;
        double timing = timingAt(t);
        if (timing <= 0) return Launch.MISSED;
        double pull = PULL_BASE + PULL_LENGTH * input.pullOrZero() + PULL_SPEED * input.speedOrZero();
        double quality = round(timing * pull);
        int hits = 1;
        int cores = timing >= 1 ? 1 : 0;
        return new Launch(quality, hits, cores, round(-1 + FLY_MIN + FLY_RANGE * quality),
                round(AIM_RANGE * input.aimOrStraight()));
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
