package org.example.game;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ein Stadion: Form und Spielwerte. Wie es aussieht, bestimmt die optische Variante ({@link #variants()}),
 * die nur das Frontend betrifft und mit dem Kampf gespeichert wird. Neue Arenen kommen über {@link #ALL}
 * dazu. Taschenwinkel und Varianten müssen zur Arena-Definition im Frontend passen
 * ({@code src/game/arenas.ts}).
 *
 * @param id              stabile ID, wird pro Kampf gespeichert
 * @param friction        Faktor auf die Reibung (1 = Standard)
 * @param ringOut         Faktor auf die Ring-Out-Chance (1 = Standard)
 * @param clash           Faktor auf die Clash-Rate (1 = Standard)
 * @param impact          Faktor auf die Treffer-Intensität (1 = Standard)
 * @param pockets         Polarwinkel (rad) der Ring-Out-Taschen
 * @param pocketHalfWidth halbe Öffnungsbreite einer Tasche (rad)
 * @param variants        IDs der optischen Varianten, die erste ist die Vorgabe
 */
public record Arena(String id, String name, double friction, double ringOut, double clash, double impact,
                    List<Double> pockets, double pocketHalfWidth, List<String> variants) {

    /** Klassische Bowl mit Tornado-Ridge und drei Taschen. */
    public static final Arena STANDARD = new Arena("standard", "Standard-Stadion", 1.0, 1.0, 1.0, 1.0,
            List.of(Math.PI / 2, 7 * Math.PI / 6, 11 * Math.PI / 6), Math.toRadians(14),
            List.of("klassik", "vulkan"));

    /**
     * Achteckiger Stahlkäfig mit flachem Boden und hohen Wänden. Die Kreisel prallen ständig von den Wänden
     * zurück (mehr Clashes), die Wände dämpfen die Treffer etwas, raus geht es nur durch vier schmale Lücken
     * (weniger Ring-Outs). Polierter Stahl reibt etwas weniger.
     */
    public static final Arena OKTAGON = new Arena("oktagon", "Stahl-Oktagon", 0.98, 0.75, 1.15, 0.95,
            List.of(Math.PI / 4, 3 * Math.PI / 4, 5 * Math.PI / 4, 7 * Math.PI / 4), Math.toRadians(9),
            List.of("chrom", "neon"));

    /**
     * Tiefer, steiler Kessel mit Kuppel in der Mitte. Die Kuppel drückt die Kreisel nach außen auf die steile
     * Wand: seltenere, aber härtere Zusammenstöße, und sechs kleine Taschen am Rand bringen mehr Ring-Outs.
     */
    public static final Arena ZYKLON = new Arena("zyklon", "Zyklon-Kessel", 1.0, 1.35, 0.92, 1.04,
            List.of(Math.PI / 6, Math.PI / 2, 5 * Math.PI / 6, 7 * Math.PI / 6, 3 * Math.PI / 2, 11 * Math.PI / 6),
            Math.toRadians(8), List.of("sturm", "sand"));

    /**
     * Weite, flache, spiegelglatte Schale: wenig Reibung, seltenere und sanftere Kontakte. Wer getroffen
     * wird, rutscht weit (mehr Ring-Outs durch zwei breite Taschen), sonst entscheidet meist die Ausdauer.
     */
    public static final Arena GLETSCHER = new Arena("gletscher", "Gletscher", 0.94, 1.5, 0.9, 0.96,
            List.of(Math.PI / 2, 3 * Math.PI / 2), Math.toRadians(18), List.of("eis", "aurora"));

    /**
     * Früherer Vulkan-Krater. Nicht mehr wählbar (der Look ist jetzt eine Variante des Standard-Stadions),
     * bleibt aber erhalten, damit gespeicherte Kämpfe weiter exakt nachgespielt werden.
     */
    public static final Arena KRATER = new Arena("krater", "Vulkan-Krater", 1.03, 2.0, 1.0, 1.0,
            List.of(0.0, Math.PI), Math.toRadians(22), List.of("vulkan"));

    private static final List<Arena> SELECTABLE = List.of(STANDARD, OKTAGON, ZYKLON, GLETSCHER);

    private static final Map<String, Arena> ALL = Map.of(STANDARD.id(), STANDARD, OKTAGON.id(), OKTAGON,
            ZYKLON.id(), ZYKLON, GLETSCHER.id(), GLETSCHER, KRATER.id(), KRATER);

    /** Wählbare Arenen. */
    public static List<Arena> all() {
        return SELECTABLE;
    }

    public static Arena standard() {
        return STANDARD;
    }

    /** Auch nicht mehr wählbare Arenen, für Replays. */
    public static Optional<Arena> find(String id) {
        return Optional.ofNullable(id == null ? null : ALL.get(id));
    }

    public boolean selectable() {
        return SELECTABLE.contains(this);
    }

    /** Gültige Variante oder die Vorgabe der Arena. */
    public String variantOrDefault(String variant) {
        return variant != null && variants.contains(variant) ? variant : variants.getFirst();
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
