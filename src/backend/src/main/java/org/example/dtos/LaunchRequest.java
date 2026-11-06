package org.example.dtos;

import java.util.List;

/**
 * Rohe Eingabe des Anspinnens.
 *
 * @param taps Zeitpunkte der Taps in ms ab Start des Zeigers auf dem eigenen Gerät, aufsteigend, höchstens drei
 */
public record LaunchRequest(List<Integer> taps) {}
