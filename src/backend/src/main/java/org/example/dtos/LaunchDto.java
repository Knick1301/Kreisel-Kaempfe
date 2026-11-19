package org.example.dtos;

import org.example.game.Launch;

/**
 * Ausgewerteter Start für Anzeige, Ergebnis und Animation.
 *
 * @param quality  0 bis 1
 * @param hits     getroffene Zonen (0–3)
 * @param cores    davon im Kern
 * @param landingX Landepunkt im Abschussrahmen (so weit fliegt der Kreisel hinein)
 * @param perfect  alle drei Zonen im Kern (sehr selten)
 * @param missed   keine Zone getroffen
 */
public record LaunchDto(double quality, int hits, int cores, Double landingX, Double landingY, boolean perfect,
                        boolean missed) {

    public static LaunchDto from(Launch l) {
        if (l == null) return null;
        return new LaunchDto(l.quality(), l.hits(), l.cores(), l.landingX(), l.landingY(), l.perfect(),
                l.missed());
    }
}
