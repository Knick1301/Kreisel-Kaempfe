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

    /**
     * Wertet die Taps aus: Jede Zone zählt mit ihrem besten Tap, höchstens drei Taps. Unplausible Eingaben
     * (außerhalb des Zeitfensters, nicht aufsteigend, mehr Taps als erlaubt) zählen als verpasster Start.
     */
    public Launch score(LaunchInput input) {
        List<Integer> taps = input == null || input.taps() == null ? List.of() : input.taps();
        if (taps.isEmpty() || taps.size() > MAX_TAPS) return Launch.MISSED;
        double[] best = new double[ZONES];
        int previous = 0;
        for (Integer t : taps) {
            if (t == null || t < previous || t > WINDOW_MS) return Launch.MISSED;
            previous = t;
            double p = needleAt(t);
            List<Double> zones = zones();
            for (int i = 0; i < ZONES; i++) {
                best[i] = Math.max(best[i], zoneScore(Math.abs(p - zones.get(i))));
            }
        }
        int hits = 0;
        int cores = 0;
        double sum = 0;
        for (double b : best) {
            if (b > 0) hits++;
            if (b >= 1) cores++;
            sum += b;
        }
        double quality = round(sum / ZONES);
        return new Launch(quality, hits, cores, round(-1 + FLY_MIN + FLY_RANGE * quality), 0.0);
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
