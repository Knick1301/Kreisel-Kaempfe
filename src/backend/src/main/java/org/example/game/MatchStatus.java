package org.example.game;

/** Ablauf eines Matches: erst Anspinnen, dann ist der Kampf simuliert und gespeichert. */
public enum MatchStatus {
    /** Mindestens eine Seite hat noch nicht angespinnt. */
    LAUNCH,
    /**
     * Beide Starts sind da, der Kampf läuft: Der Spieler kann seine Ult auslösen, der Kampf ist nur vorläufig
     * simuliert und noch nicht gespeichert.
     */
    FIGHT,
    /** Kampf beendet und gespeichert. */
    DONE
}
