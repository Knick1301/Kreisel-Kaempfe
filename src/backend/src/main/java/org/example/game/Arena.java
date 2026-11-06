package org.example.game;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ein Stadion. Neue Arenen kommen über {@link #ALL} dazu.
 * Die Taschenwinkel müssen zur Arena-Definition im Frontend passen ({@code src/game/arenas.ts}).
 *
 * @param id              stabile ID, wird pro Kampf gespeichert
 * @param friction        Faktor auf die Reibung (1 = Standard)
 * @param ringOut         Faktor auf die Ring-Out-Chance (1 = Standard)
 * @param pockets         Polarwinkel (rad) der Ring-Out-Taschen
 * @param pocketHalfWidth halbe Öffnungsbreite einer Tasche (rad)
 */
public record Arena(String id, String name, double friction, double ringOut, List<Double> pockets,
                    double pocketHalfWidth) {

    public static final Arena STANDARD = new Arena("standard", "Standard-Stadion", 1.0, 1.0,
            List.of(Math.PI / 2, 7 * Math.PI / 6, 11 * Math.PI / 6), Math.toRadians(14));

    /**
     * Steiler, kleinerer Krater mit rauem Boden (mehr Reibung) und zwei breiten Taschen: Ring-Outs werden
     * deutlich wahrscheinlicher, Ausdauer-Kreisel haben es etwas schwerer.
     */
    public static final Arena KRATER = new Arena("krater", "Vulkan-Krater", 1.03, 2.0,
            List.of(0.0, Math.PI), Math.toRadians(22));

    private static final Map<String, Arena> ALL = Map.of(STANDARD.id(), STANDARD, KRATER.id(), KRATER);

    public static List<Arena> all() {
        return List.of(STANDARD, KRATER);
    }

    public static Arena standard() {
        return STANDARD;
    }

    public static Optional<Arena> find(String id) {
        return Optional.ofNullable(id == null ? null : ALL.get(id));
    }

    /** Index der Tasche, die dem Winkel am nächsten liegt. */
    public int nearestPocket(double angle) {
        int best = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < pockets.size(); i++) {
            double d = Math.abs(wrap(angle - pockets.get(i)));
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    /** Winkel auf (-π, π]. */
    static double wrap(double angle) {
        double a = angle % (2 * Math.PI);
        if (a > Math.PI) a -= 2 * Math.PI;
        if (a <= -Math.PI) a += 2 * Math.PI;
        return a;
    }
}
