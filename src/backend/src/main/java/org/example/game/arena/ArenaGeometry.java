package org.example.game.arena;

import org.example.game.Arena;

import java.util.Map;

/**
 * Form der Bowl für die Live-Physik, gleiche Werte wie {@code src/game/arenas.ts} im Frontend: Radius,
 * Krümmung, Tornado-Ridge und (Zyklon) Kuppel. Taschen und Spielwerte kommen aus {@link Arena}.
 */
public record ArenaGeometry(Arena arena, double radius, double curvature, double ridgeR, double ridgeHeight,
                            double ridgeWidth, double domeR, double domeHeight) {

    private static final Map<String, ArenaGeometry> ALL = Map.of(
            "standard", new ArenaGeometry(Arena.STANDARD, 4, 0.05, 3.05, 0.07, 0.22, 0, 0),
            "oktagon", new ArenaGeometry(Arena.OKTAGON, 4.1, 0.012, 3.0, 0, 0.3, 0, 0),
            "zyklon", new ArenaGeometry(Arena.ZYKLON, 3.9, 0.085, 2.65, 0.05, 0.24, 1.25, 0.3),
            "gletscher", new ArenaGeometry(Arena.GLETSCHER, 4.6, 0.022, 3.35, 0.035, 0.45, 0, 0),
            "krater", new ArenaGeometry(Arena.KRATER, 3.6, 0.075, 2.55, 0.09, 0.2, 0, 0));

    public static ArenaGeometry of(Arena arena) {
        ArenaGeometry g = ALL.get(arena.id());
        if (g == null) throw new IllegalArgumentException("Keine Geometrie für Arena " + arena.id());
        return g;
    }

    /** So weit dürfen Kreisel-Mittelpunkte vom Zentrum weg, ohne über den Rand zu ragen. */
    public double playRadius() {
        return radius - 0.72;
    }

    /** Steigung dy/dr der Bowl. */
    public double slope(double r) {
        double d = (r - ridgeR) / ridgeWidth;
        double slope = 2 * curvature * r - ((2 * d) / ridgeWidth) * ridgeHeight * Math.exp(-d * d);
        if (domeR > 0 && r < domeR) {
            double u = r / domeR;
            slope += (-4 * domeHeight * u * (1 - u * u)) / domeR;
        }
        return slope;
    }

    /** Index der Tasche an diesem Winkel, sonst -1. */
    public int pocketAt(double angle) {
        var pockets = arena.pockets();
        for (int i = 0; i < pockets.size(); i++) {
            if (Math.abs(wrap(angle - pockets.get(i))) <= arena.pocketHalfWidth() + 1e-9) return i;
        }
        return -1;
    }

    static double wrap(double angle) {
        double a = angle % (2 * Math.PI);
        if (a > Math.PI) a -= 2 * Math.PI;
        if (a <= -Math.PI) a += 2 * Math.PI;
        return a;
    }
}
