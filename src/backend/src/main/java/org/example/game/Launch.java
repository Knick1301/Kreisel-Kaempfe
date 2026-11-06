package org.example.game;

/**
 * Ausgewerteter Start eines Kämpfers. Die Simulation nutzt nur {@link #quality()}, der Landepunkt ist für
 * die Animation (so weit fliegt der Kreisel hinein).
 *
 * @param quality  0 bis 1: Summe der drei Zonenwerte geteilt durch drei
 * @param hits     getroffene Zonen (0–3)
 * @param cores    davon im Kern getroffen (0–3)
 * @param landingX Landepunkt im Abschussrahmen (Launcher bei (−1, 0), Mitte bei (0, 0))
 * @param landingY Landepunkt seitlich
 */
public record Launch(double quality, int hits, int cores, Double landingX, Double landingY) {

    public static final Launch MISSED = new Launch(0, 0, 0, -1 + LaunchRules.FLY_MIN, 0.0);

    /** Keine Zone getroffen. */
    public boolean missed() {
        return hits == 0;
    }

    /** Alle drei Zonen im Kern: sehr selten. */
    public boolean perfect() {
        return hits == LaunchRules.ZONES && quality >= LaunchRules.PERFECT_QUALITY;
    }
}
