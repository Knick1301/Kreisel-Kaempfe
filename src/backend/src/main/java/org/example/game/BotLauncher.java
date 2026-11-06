package org.example.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Anspinnen des KI-Gegners. Der Bot gibt dieselben rohen Taps ab wie ein Mensch und wird genauso
 * ausgewertet: Er nimmt sich die Zonen der Reihe nach vor, sobald der Zeiger auf sie zuläuft, und tippt mit
 * menschlicher Streuung. Er kennt die Eingabe des Spielers nicht, beide Starts sind verdeckt.
 */
public final class BotLauncher {

    private BotLauncher() {}

    /** Mindestabstand zwischen zwei Taps (Reaktion, Finger). */
    private static final int MIN_GAP_MS = 220;

    /** @param skill 0 = sehr ungenau, 1 = sehr genau */
    public static LaunchInput input(LaunchChallenge c, Random rng, double skill) {
        double sigma = 75 * (1.25 - Math.max(0, Math.min(1, skill)));
        List<Integer> taps = new ArrayList<>();
        boolean[] done = new boolean[LaunchRules.ZONES];
        int t = 0;
        for (int n = 0; n < LaunchRules.MAX_TAPS; n++) {
            // nächster Moment, in dem der Zeiger über eine noch offene Zonenmitte läuft
            int next = -1;
            int zone = -1;
            for (int ms = t; ms <= LaunchRules.WINDOW_MS && next < 0; ms++) {
                double p0 = c.needleAt(ms);
                double p1 = c.needleAt(ms + 1);
                for (int i = 0; i < LaunchRules.ZONES; i++) {
                    double z = c.zones().get(i);
                    if (!done[i] && (p0 - z) * (p1 - z) <= 0) {
                        next = ms;
                        zone = i;
                        break;
                    }
                }
            }
            if (next < 0) break;
            done[zone] = true;
            int tap = (int) Math.round(next + rng.nextGaussian() * sigma);
            tap = Math.max(t, Math.min(LaunchRules.WINDOW_MS, tap));
            taps.add(tap);
            t = tap + MIN_GAP_MS;
        }
        return new LaunchInput(taps);
    }
}
