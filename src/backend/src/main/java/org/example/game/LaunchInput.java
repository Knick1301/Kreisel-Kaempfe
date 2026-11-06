package org.example.game;

import java.util.List;

/**
 * Rohe Eingabe des Anspinnens: Zeitpunkte der Taps in ms ab dem Start des Zeigers auf dem eigenen Gerät.
 *
 * @param taps höchstens {@link LaunchRules#MAX_TAPS}, aufsteigend; leer oder {@code null} = kein Start
 */
public record LaunchInput(List<Integer> taps) {

    public static final LaunchInput NONE = new LaunchInput(List.of());
}
