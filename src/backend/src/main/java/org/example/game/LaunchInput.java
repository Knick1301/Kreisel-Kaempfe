package org.example.game;

import java.util.List;

/**
 * Rohe Eingabe des Anspinnens mit Reißleine, gemessen ab dem Start des Zeigers auf dem eigenen Gerät.
 *
 * @param taps  genau ein Zeitpunkt: der Moment des Loslassens (ms); leer oder {@code null} = kein Start
 * @param aim   Zugrichtung, −1 (ganz links) bis 1 (ganz rechts), {@code null} = gerade. Bestimmt nur, wohin der
 *              Kreisel in die Bowl fliegt, nicht die Wertung.
 * @param pull  Zuglänge 0–1 ({@code null} = 0)
 * @param speed Tempo des letzten Rucks 0–1 ({@code null} = 0)
 */
public record LaunchInput(List<Integer> taps, Double aim, Double pull, Double speed) {

    public static final LaunchInput NONE = new LaunchInput(List.of());

    public LaunchInput(List<Integer> taps) {
        this(taps, null, null, null);
    }

    public LaunchInput(List<Integer> taps, Double aim) {
        this(taps, aim, null, null);
    }

    /** Zuglänge auf [0, 1] begrenzt. */
    public double pullOrZero() {
        return unit(pull);
    }

    /** Zugtempo auf [0, 1] begrenzt. */
    public double speedOrZero() {
        return unit(speed);
    }

    private static double unit(Double v) {
        if (v == null || v.isNaN() || v.isInfinite()) return 0;
        return Math.max(0, Math.min(1, v));
    }

    /** Zugrichtung, auf [−1, 1] begrenzt, 0 bei fehlender oder ungültiger Angabe. */
    public double aimOrStraight() {
        if (aim == null || aim.isNaN() || aim.isInfinite()) return 0;
        return Math.max(-1, Math.min(1, aim));
    }
}
