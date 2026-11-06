package org.example.game;

/**
 * Ausgewerteter Start eines Kämpfers. Die Simulation nutzt nur {@link #quality()}, der Landepunkt ist für
 * die Animation (so weit fliegt der Kreisel hinein).
 *
 * @param quality  0 bis 1: Timing mal Zugfaktor
 * @param hits     1, wenn beim Loslassen eine Zone getroffen war (bei Kämpfen vor der Reißleine: 0–3)
 * @param cores    1, wenn im Kern losgelassen (früher: 0–3)
 * @param landingX Landepunkt im Abschussrahmen (Launcher bei (−1, 0), Mitte bei (0, 0))
 * @param landingY Landepunkt seitlich
 */
public record Launch(double quality, int hits, int cores, Double landingX, Double landingY) {

    public static final Launch MISSED = new Launch(0, 0, 0, -1 + LaunchRules.FLY_MIN, 0.0);

    /** Keine Zone getroffen. */
    public boolean missed() {
        return hits == 0;
    }

    /** Im Kern losgelassen und voll durchgezogen: sehr selten. */
    public boolean perfect() {
        return cores >= 1 && quality >= LaunchRules.PERFECT_QUALITY;
    }
}
