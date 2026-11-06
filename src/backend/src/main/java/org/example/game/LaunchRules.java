package org.example.game;

/**
 * Regeln des Anspinnens mit dem Schwungmesser. Ein Zeiger pendelt über einen Bogen von 0 (links) bis 1
 * (rechts) und zurück: an den Enden langsam, in der Mitte schnell ({@code p(t) = ½ − ½·cos(2π·t/period)}).
 * Auf dem Bogen liegen drei Zonen, jede mit einem schmalen Kern. Der Spieler hat {@link #WINDOW_MS} ms und
 * genau {@link #MAX_TAPS} Taps. Jeder Tap zählt für die Zone, in der der Zeiger gerade steht, jede Zone
 * höchstens einmal (der beste Tap). Ein Tap daneben ist verschenkt.
 *
 * <p>Zeiten misst der Client ab dem Start des Zeigers auf seinem eigenen Gerät, die Latenz spielt keine
 * Rolle. Gewertet wird nur auf dem Server.
 */
public final class LaunchRules {

    private LaunchRules() {}

    /** So lange läuft der Zeiger. */
    public static final int WINDOW_MS = 3_000;
    /** So viele Taps hat man, einen pro Zone. */
    public static final int MAX_TAPS = 3;
    /** Dauer einmal hin und zurück, pro Match leicht unterschiedlich. */
    public static final int PERIOD_MIN_MS = 1_250;
    public static final int PERIOD_MAX_MS = 1_450;

    /** Anzahl Zonen auf dem Bogen. */
    public static final int ZONES = 3;
    /** Halbe Breite einer Zone (orange, auf dem Bogen 0–1). */
    public static final double ZONE_HALF = 0.05;
    /** Halbe Breite des Kerns (türkis): volle Punkte. */
    public static final double CORE_HALF = 0.014;
    /** Am Rand der Zone gibt es noch diesen Anteil. */
    public static final double EDGE_SCORE = 0.5;
    /** Zonenmitten liegen in diesem Bereich … */
    public static final double ZONE_MIN = 0.12;
    public static final double ZONE_MAX = 0.88;
    /** … und mindestens so weit auseinander. */
    public static final double ZONE_GAP = 0.22;

    /** Ab dieser Qualität gilt der Start als perfekt (alle drei Kerne). */
    public static final double PERFECT_QUALITY = 0.97;

    /** Flugweite in die Bowl im Abschussrahmen: schwacher Start knapp hinein, starker weit. */
    public static final double FLY_MIN = 0.25;
    public static final double FLY_RANGE = 0.3;

    /**
     * Frist ab Match-Start fürs Anspinnen (inklusive Ansehen des Gegners), danach zählt ein fehlender Start als
     * verpasst. Für PvP kommt später ein gemeinsamer Bereit-Schritt dazu, ab dem der Zeiger läuft.
     */
    public static final int SUBMIT_TIMEOUT_MS = 120_000;

    /** Mittlere Stärke des KI-Gegners (0 = sehr ungenau, 1 = sehr genau). */
    public static final double BOT_SKILL = 0.5;
}
