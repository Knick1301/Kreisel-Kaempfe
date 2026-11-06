package org.example.game;

import java.util.List;

/**
 * Wie eine Seite ihre Ult auslöst.
 *
 * @param manual {@code false}: automatisch, sobald die Leiste voll ist (KI, Lobby, Schnelles Match).
 *               {@code true}: Die volle Leiste wird gehalten, bis der Spieler drückt.
 * @param times  Kampfzeitpunkte (ms), zu denen der Spieler gedrückt hat, aufsteigend. Die Ult löst im ersten
 *               Tick ab diesem Zeitpunkt aus.
 */
public record UltInput(boolean manual, List<Integer> times) {

    public static final UltInput AUTO = new UltInput(false, List.of());

    public UltInput {
        times = times == null ? List.of() : List.copyOf(times);
    }

    public static UltInput manual(List<Integer> times) {
        return new UltInput(true, times);
    }

    /** Neuer Druck dazu. */
    public UltInput with(int t) {
        var list = new java.util.ArrayList<>(times);
        list.add(t);
        return new UltInput(true, list);
    }
}
