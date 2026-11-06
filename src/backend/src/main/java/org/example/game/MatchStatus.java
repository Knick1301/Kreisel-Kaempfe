package org.example.game;

/** Ablauf eines Matches: erst Anspinnen, dann ist der Kampf simuliert und gespeichert. */
public enum MatchStatus {
    /** Mindestens eine Seite hat noch nicht angespinnt. */
    LAUNCH,
    /** Beide Starts sind da (oder die Zeit ist abgelaufen), der Kampf ist gespeichert. */
    DONE
}
