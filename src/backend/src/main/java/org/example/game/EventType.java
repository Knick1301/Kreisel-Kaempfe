package org.example.game;

public enum EventType {
    LAUNCH,
    TORNADO,
    ENGAGE,
    CLASH,
    DEFLECT,
    NEAR_RING_OUT,
    WOBBLE,
    BURST,
    RING_OUT,
    SPIN_OUT,
    TIMEOUT,
    /** Ult löst aus: side, ult, intensity = Stärke, durationMs bei Schild/Ansturm/Zweiter Wind */
    ULT,
    /** Leiste voll, die Ult wartet auf den Spieler (nur bei manueller Ult): side, ult */
    ULT_READY,
    /** Ult-Wirkung endet (Schild, Ansturm, Zweiter Wind): side, ult */
    ULT_END
}
