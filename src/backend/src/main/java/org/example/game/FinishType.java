package org.example.game;

public enum FinishType {
    /** Kreisel zerfällt in seine Teile. */
    BURST,
    /** Kreisel wird aus dem Stadion in eine Tasche geschleudert. */
    RING_OUT,
    /** Kreisel bleibt stehen. */
    SPIN_OUT,
    /** Zeitlimit erreicht, mehr Restenergie gewinnt. */
    DECISION
}
