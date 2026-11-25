package org.example.game;

/**
 * Ultimative Fähigkeit eines Kreisels, festgelegt durch sein Fusion Wheel. Die Leiste lädt sich durch
 * Treffer, Abwehr und mit der Zeit, ein gutes Anspinnen lädt sie vor und macht die Ult stärker.
 * Formeln und Werte stehen in {@link BattleTuning} (Abschnitt Ult).
 */
public enum UltType {
    /** Vernichtungsschlag: ein einzelner, nicht abwehrbarer Treffer mit stark erhöhter Wucht. */
    SMASH("Vernichtungsschlag"),
    /** Schild: für einige Sekunden prallt jeder Angriff ab, der Angreifer bekommt den Rückstoß. */
    SHIELD("Schildwall"),
    /** Ansturm: für einige Sekunden greift der Kreisel pausenlos an, kostet dafür eigenen Spin. */
    RUSH("Ansturm"),
    /** Zweiter Wind: holt Spin zurück und senkt kurz die Reibung. */
    SURGE("Zweiter Wind");

    private final String label;

    UltType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Ult für Kreisel ohne Fusion-Wheel-Angabe (KI-Gegner, Tests): passend zum Stil. */
    public static UltType forArchetype(Archetype archetype) {
        return switch (archetype) {
            case ATTACK -> SMASH;
            case DEFENSE -> SHIELD;
            case STAMINA -> SURGE;
            case BALANCE -> RUSH;
        };
    }

    /** Liest den Namen tolerant ein, {@code null} bei leer oder unbekannt. */
    public static UltType parse(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
