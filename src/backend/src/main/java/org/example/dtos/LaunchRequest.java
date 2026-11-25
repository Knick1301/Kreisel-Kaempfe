package org.example.dtos;

import java.util.List;

/**
 * Rohe Eingabe des Anspinnens.
 *
 * @param aim  Zugrichtung des letzten Zugs, −1 bis 1 (optional)
 * @param pull  Zuglänge 0–1
 * @param speed Tempo des letzten Rucks 0–1
 * @param taps  ein Zeitpunkt: Loslassen in ms ab Start des Zeigers auf dem eigenen Gerät
 */
public record LaunchRequest(List<Integer> taps, Double aim, Double pull, Double speed) {}
