package org.example.game;

/**
 * Regeln des Anspinnens mit Reißleine. Ein Zeiger pendelt über einen Bogen von 0 (links) bis 1 (rechts) und
 * zurück: an den Enden langsam, in der Mitte schnell ({@code p(t) = ½ − ½·cos(2π·t/period)}). Auf dem Bogen
 * liegen drei Zonen, jede mit einem schmalen Kern. Der Spieler greift die Leine, zieht und lässt los, genau
 * einmal innerhalb von {@link #WINDOW_MS} ms:
 * <pre>
 * Timing   Zonenwert beim Loslassen (Kern 1, Rand 0,5, daneben 0 = verpasst)
 * Qualität Timing · (PULL_BASE + PULL_LENGTH · Zuglänge + PULL_SPEED · Zugtempo)
 * </pre>
 * Zuglänge und -tempo misst der Client (0–1). Wer nur kurz zuckt, startet auch mit gutem Timing schwach.
 *
 * <p>Zeiten misst der Client ab dem Start des Zeigers auf seinem eigenen Gerät, die Latenz spielt keine
 * Rolle. Gewertet wird nur auf dem Server.
 */
public final class LaunchRules {

    private LaunchRules() {}

    /** So lange läuft der Zeiger. */
    public static final int WINDOW_MS = 3_000;
    /** Genau ein Loslassen. */
    public static final int MAX_TAPS = 1;
    /** Anteil der Qualität, den perfektes Timing auch ohne Zug bringt … */
    public static final double PULL_BASE = 0.45;
    /** … dazu so viel für die volle Zuglänge … */
    public static final double PULL_LENGTH = 0.35;
    /** … und so viel für einen schnellen Ruck. */
    public static final double PULL_SPEED = 0.2;
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

    /** Ab dieser Qualität gilt der Start als perfekt (Kern, voll und schnell durchgezogen). */
    public static final double PERFECT_QUALITY = 0.97;

    /** Flugweite in die Bowl im Abschussrahmen: schwacher Start knapp hinein, starker weit. */
    public static final double FLY_MIN = 0.25;
    public static final double FLY_RANGE = 0.3;
    /** Seitliche Ablenkung des Landepunkts bei voller Zugrichtung (Abschussrahmen). */
    public static final double AIM_RANGE = 0.35;

    /**
     * Frist ab Match-Start fürs Anspinnen (inklusive Ansehen des Gegners), danach zählt ein fehlender Start als
     * verpasst. Für PvP kommt später ein gemeinsamer Bereit-Schritt dazu, ab dem der Zeiger läuft.
     */
    public static final int SUBMIT_TIMEOUT_MS = 120_000;

    /** Mittlere Stärke des KI-Gegners (0 = sehr ungenau, 1 = sehr genau). */
    public static final double BOT_SKILL = 0.5;
}
