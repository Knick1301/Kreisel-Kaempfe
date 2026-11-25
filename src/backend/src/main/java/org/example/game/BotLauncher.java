package org.example.game;

import java.util.List;
import java.util.Random;

/**
 * Anspinnen des KI-Gegners mit Reißleine. Der Bot gibt dieselbe rohe Eingabe ab wie ein Mensch und wird genauso
 * ausgewertet: Er wartet, bis der Zeiger auf eine Zone zuläuft, lässt mit menschlicher Streuung los und zieht
 * unterschiedlich kräftig. Er kennt die Eingabe des Spielers nicht, beide Starts sind verdeckt.
 */
public final class BotLauncher {

    private BotLauncher() {}

    /** Frühester Zeitpunkt, zu dem der Bot loslässt (Greifen, Ausholen). */
    private static final int MIN_RELEASE_MS = 600;

    /** @param skill 0 = sehr ungenau, 1 = sehr genau */
    public static LaunchInput input(LaunchChallenge c, Random rng, double skill) {
        double s = Math.max(0, Math.min(1, skill));
        double sigma = 75 * (1.25 - s);
        // Ziel: nächster Moment ab MIN_RELEASE_MS, in dem der Zeiger über eine Zonenmitte läuft
        int target = -1;
        for (int ms = MIN_RELEASE_MS + rng.nextInt(500); ms < LaunchRules.WINDOW_MS && target < 0; ms++) {
            double p0 = c.needleAt(ms);
            double p1 = c.needleAt(ms + 1);
            for (double z : c.zones()) {
                if ((p0 - z) * (p1 - z) <= 0) {
                    target = ms;
                    break;
                }
            }
        }
        if (target < 0) return LaunchInput.NONE;
        int release = (int) Math.round(target + rng.nextGaussian() * sigma);
        release = Math.max(0, Math.min(LaunchRules.WINDOW_MS, release));
        double pull = clamp(0.5 + 0.35 * s + rng.nextGaussian() * 0.15);
        double speed = clamp(0.45 + 0.35 * s + rng.nextGaussian() * 0.18);
        double aim = clamp(rng.nextGaussian() * 0.35, -1);
        return new LaunchInput(List.of(release), aim, pull, speed);
    }

    private static double clamp(double v) {
        return clamp(v, 0);
    }

    private static double clamp(double v, double min) {
        return Math.max(min, Math.min(1, v));
    }
}
