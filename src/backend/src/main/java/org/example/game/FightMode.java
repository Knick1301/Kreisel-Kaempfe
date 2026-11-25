package org.example.game;

/** Kampfart für Lobby, Spielersuche und Herausforderungen. */
public enum FightMode {
    /** berechneter Kampf mit Anspinnen und Ult */
    CLASSIC,
    /** Echtzeit-Physik mit Steuerung (WASD) */
    ARENA;

    /** Leer oder unbekannt = klassisch. */
    public static FightMode parse(String name) {
        if (name == null || name.isBlank()) return CLASSIC;
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return CLASSIC;
        }
    }
}
