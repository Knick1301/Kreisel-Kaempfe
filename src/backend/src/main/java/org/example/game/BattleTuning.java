package org.example.game;

import java.util.Set;

/**
 * Alle Balancing-Konstanten der Kampfsimulation an einer Stelle.
 *
 * <p>Wer hier etwas ändert, muss {@link #SIM_VERSION} erhöhen: Gespeicherte Kämpfe werden mit Seed und
 * Stat-Snapshots neu simuliert. Mit anderen Konstanten käme ein anderer Verlauf heraus, deshalb liefert das
 * Replay für Kämpfe einer älteren Version keine Timeline mehr (das Ergebnis bleibt erhalten).
 *
 * <p>Die Werte sind per Monte-Carlo-Lauf kalibriert, siehe {@code BattleBalanceTest} und
 * {@code docs/architektur/kampfsystem.md}.
 */
public final class BattleTuning {

    private BattleTuning() {}

    /** Version der Simulation. Bei jeder Änderung an Formeln oder Konstanten erhöhen. */
    public static final int SIM_VERSION = 6;
    /**
     * Versionen, deren Kämpfe die aktuelle Simulation exakt nachspielt. Version 6 hat die Grundwerte neu
     * eingestellt (weniger Reibung, mehr Clashes, stärkere Ults), ältere Kämpfe behalten ihr Ergebnis, aber
     * keine Animation mehr.
     */
    public static final Set<Integer> REPLAYABLE_VERSIONS = Set.of(6);
    /** Ab dieser Version gibt es Ults. */
    public static final int ULTS_SINCE = 3;
    /** Ab dieser Version gilt {@link #TORNADO_FRICTION}. */
    public static final int TORNADO_FRICTION_SINCE = 4;

    // ---------------------------------------------------------------- Zeit

    /** Simulationsschritt: 20 Ticks pro Sekunde. */
    public static final int TICK_MS = 50;
    /** Abstand der Energie-Samples in der Timeline. */
    public static final int SAMPLE_MS = 100;
    /** Zeitlimit, danach entscheidet die Restenergie. */
    public static final int TIME_LIMIT_MS = 35_000;
    /** Vor diesem Zeitpunkt gibt es keine KOs, so dauert jeder Kampf mindestens so lange. */
    public static final int MIN_KO_MS = 12_000;
    /** Danach steigt die KO-Chance über diese Dauer linear auf 100 % an. */
    public static final int KO_RAMP_MS = 4_000;
    /** Ende des Launch, ab hier kreisen die Kreisel am Rand (Tornado). */
    public static final int TORNADO_MS = 1_000;
    /** Ab hier greifen die Kreisel richtig an. */
    public static final int ENGAGE_MS = 3_500;
    /** Mindestabstand zweier Clashes, damit die Animation Anlauf und Abprall zeigen kann. */
    public static final int CLASH_COOLDOWN_MS = 700;

    // ---------------------------------------------------------------- Normalisierung

    // Referenzwerte = Maximum bzw. Minimum, das mit den Teilen aus parts/*.json erreichbar ist
    // (TuningCatalogTest prüft das). Die Minima begrenzen die Werte der KI-Gegner.
    public static final int ATTACK_MIN = 1;
    public static final int DEFENSE_MIN = 3;
    public static final int STAMINA_MIN = 6;
    public static final int DASH_MIN = 2;
    public static final int BURST_MIN = 2;
    public static final int ATTACK_REF = 7;
    public static final int DEFENSE_REF = 12;
    public static final int STAMINA_REF = 16;
    public static final int DASH_REF = 8;
    public static final int BURST_REF = 4;
    public static final int WEIGHT_MIN = 71;
    public static final int WEIGHT_MAX = 86;
    /** Normalisierte Werte werden auf [0, NORM_MAX] begrenzt (Werte außerhalb der Teile gibt es nur in Tests). */
    public static final double NORM_MAX = 2.0;

    /** Ab diesem Anteil am Stil-Vektor bekommt ein Kreisel ein Archetyp-Label, sonst „Balance“. */
    public static final double ARCHETYPE_SHARE = 0.42;
    /** Matchup-Kanten darunter gelten in der Anzeige als ausgeglichen. */
    public static final double EVEN_EDGE = 0.05;

    // ---------------------------------------------------------------- KI-Gegner

    /** Budget des KI-Gegners relativ zum Spieler, ± (die Tests erlauben ±10 % inklusive Rundung). */
    public static final double BOT_BUDGET_SPREAD = 0.08;
    /** Wahrscheinlichkeit, dass ein KI-Gegner ohne Archetyp-Bias gebaut wird. */
    public static final double BOT_BALANCE_CHANCE = 0.15;

    // ---------------------------------------------------------------- Launch & Reibung

    /** Ohne Anspinnen (Version 1, Schnelles Match, Lobby): Startenergie zufällig zwischen LAUNCH_MIN und 1. */
    public static final double LAUNCH_MIN = 0.86;
    /**
     * Mit Anspinnen: Startenergie LAUNCH_SKILL_MIN + LAUNCH_SKILL_RANGE · Qualität + Zufallsanteil. Der Mittelwert
     * typischer Starts liegt nahe am gewürfelten Mittel ohne Anspinnen, damit das Archetyp-Balancing hält.
     */
    public static final double LAUNCH_SKILL_MIN = 0.843;
    public static final double LAUNCH_SKILL_RANGE = 0.025;
    /** Kleiner Zufallsanteil der Startenergie beim Anspinnen. */
    public static final double LAUNCH_NOISE = 0.12;
    /** Initiative: Angriffsgewicht ±INITIATIVE · Differenz der Launch-Qualitäten … */
    public static final double INITIATIVE = 0.16;
    /** … bis zu diesem Zeitpunkt. */
    public static final int INITIATIVE_MS = 8_000;
    /** Zufällige Abweichung der Reibung pro Kampf (Spitze, Untergrund), ±. */
    public static final double FRICTION_JITTER = 0.10;
    /** Grundreibung pro Sekunde (Anteil der Energie). */
    public static final double FRICTION_BASE = 1 / 54.0;
    /** Schwere Kreisel reiben mehr. */
    public static final double FRICTION_WEIGHT = 0.02;
    /** Defensive Spin Tracks reiben mehr (breite Auflage). */
    public static final double FRICTION_DEFENSE = 0.012;
    /**
     * Anteil der Reibung bis ENGAGE_MS: Frisch angespinnte Kreisel kreisen ruhig auf der Ridge, der Spin hält
     * am Anfang länger. Danach volle Reibung, damit Ausdauer ihren Vorteil behält.
     */
    public static final double TORNADO_FRICTION = 0.7;
    /** Ausdauer senkt die Reibung. */
    public static final double FRICTION_STAMINA = 0.1;
    /** Unter dieser Energie taumelt der Kreisel und verliert schneller Spin. */
    public static final double WOBBLE_ENERGY = 0.30;
    /** Zusätzliche Reibung beim Taumeln (bei Energie 0 voll). */
    public static final double WOBBLE_FRICTION = 0.8;
    /** Vor MIN_KO_MS fällt die Energie nicht unter diesen Wert. */
    public static final double EARLY_ENERGY_FLOOR = 0.05;

    // ---------------------------------------------------------------- Kollisionen

    /** Grundrate der Clashes pro Sekunde in der Angriffsphase. */
    public static final double CLASH_RATE = 1.05;
    /** Sockel der Clash-Rate, auch ohne Dash treffen sich die Kreisel. */
    public static final double CLASH_RATE_BASE = 0.35;
    /** Anteil der Clash-Rate in der Tornado-Phase. */
    public static final double TORNADO_CLASH_FACTOR = 0.35;
    /** Grundaggression, dazu kommt der Angriffsanteil am Stil. */
    public static final double AGGRESSION_BASE = 0.3;
    /** Chance anzugreifen ∝ ATTACKER_BASE + Angriff + ATTACKER_DASH · Dash. */
    public static final double ATTACKER_BASE = 0.15;
    public static final double ATTACKER_DASH = 0.5;
    /** Skalierung der Clash-Intensität. */
    public static final double INTENSITY_BASE = 0.95;
    /** Schwere Kreisel treffen härter (Impuls). */
    public static final double INTENSITY_WEIGHT = 0.5;
    /** Ausdauer-Kreisel weichen aus, Treffer streifen sie eher: · (1 − EVASION · Ausdauer-Anteil). */
    public static final double EVASION = 0.88;
    /** In der Tornado-Phase sind Kontakte schwächer. */
    public static final double TORNADO_INTENSITY = 0.5;

    // ---------------------------------------------------------------- Abwehr & Schaden

    /** Abwehrchance = BASE + DEFENSE · (Verteidigung − ATTACK · Angriff des Gegners). */
    public static final double DEFLECT_BASE = 0.25;
    public static final double DEFLECT_DEFENSE = 0.8;
    public static final double DEFLECT_ATTACK = 0.6;
    public static final double DEFLECT_MIN = 0.05;
    public static final double DEFLECT_MAX = 0.85;
    /** Rückstoß: so viel Spin verliert ein Angreifer, dessen Treffer abprallt. */
    public static final double RECOIL = 0.021;
    /** Anteil von HIT, den der Verteidiger bei einer Abwehr trotzdem verliert. */
    public static final double DEFLECT_DEFENDER_LOSS = 0.15;
    /** Spinverlust des Getroffenen bei einem Volltreffer. */
    public static final double HIT = 0.09;
    /** Verteidigung dämpft Volltreffer. */
    public static final double ABSORB = 0.45;
    /** Anteil von HIT, den der Angreifer bei einem Volltreffer selbst verliert. */
    public static final double HIT_ATTACKER_LOSS = 0.5;

    // ---------------------------------------------------------------- KOs

    /** Burst-Chance pro Volltreffer = BASE · I² · (1 − RESIST · BR) · (1 + FRAGILITY · Ausdauer-Anteil) · Low-Energy. */
    public static final double BURST_BASE = 0.14;
    public static final double BURST_RESIST = 0.7;
    /** Ausdauer-Kreisel sind locker gebaut und bursten leichter. */
    public static final double FRAGILITY = 0.45;
    /** KO-Chancen steigen bei wenig Energie: · (1 + LOW_ENERGY_KO · (1 − E)). */
    public static final double LOW_ENERGY_KO = 2.0;
    /** Ring-Out-Chance pro Volltreffer = BASE · I² · (WEIGHT − Gewicht) · (1 − DEFENSE · Verteidigung) · Low-Energy. */
    public static final double RING_OUT_BASE = 0.1;
    public static final double RING_OUT_WEIGHT = 1.4;
    public static final double RING_OUT_DEFENSE = 0.5;
    /** Chance, dass sich ein abgewehrter Angreifer selbst rausschießt (wie RING_OUT, mit Gewichtsverhältnis). */
    public static final double SELF_RING_OUT = 0.05;
    /** Knapp-daneben-Ereignis, wenn der Zufallswert unter NEAR · Ring-Out-Chance liegt. */
    public static final double NEAR_RING_OUT = 2.5;
    public static final int NEAR_COOLDOWN_MS = 2_500;

    // ---------------------------------------------------------------- Ult

    /** Ult-Leiste 0–1, voll = Ult löst aus (frühestens ab ENGAGE_MS). Laden pro Sekunde … */
    public static final double ULT_TIME = 0.025;
    /** … pro Volltreffer, den man landet … */
    public static final double ULT_HIT = 0.12;
    /** … pro Volltreffer, den man einsteckt … */
    public static final double ULT_TAKEN = 0.08;
    /** … pro abgewehrtem Angriff … */
    public static final double ULT_DEFLECT = 0.07;
    /** … und pro eigenem Angriff, der abprallt. */
    public static final double ULT_DEFLECTED = 0.05;
    /**
     * Nach dem Vernichtungsschlag lädt die Leiste so lange nicht. Schild, Ansturm und Zweiter Wind laden während
     * ihrer Wirkung ohnehin nicht; so haben alle Ults denselben Rhythmus.
     */
    public static final int SMASH_COOLDOWN_MS = 4_500;
    /** Gutes Anspinnen lädt die Leiste vor: ULT_LAUNCH_START · Qualität. */
    public static final double ULT_LAUNCH_START = 0.15;
    /** Stärke der Ult: POWER_BASE + POWER_LAUNCH · Qualität (+ PERFECT bei perfektem Start), ohne Anspinnen 1. */
    public static final double ULT_POWER_BASE = 0.85;
    public static final double ULT_POWER_LAUNCH = 0.15;
    public static final double ULT_POWER_PERFECT = 0.05;

    /** Vernichtungsschlag: Ausholen bis zum Treffer. */
    public static final int SMASH_WINDUP_MS = 500;
    /** Intensität · (1 + BOOST · Stärke), mindestens MIN · Stärke. */
    public static final double SMASH_BOOST = 0.95;
    public static final double SMASH_MIN_INTENSITY = 0.3;
    /** Zusätzlicher Spinschaden des Schlags. */
    public static final double SMASH_DAMAGE = 2.0;
    /** Trifft der Schlag auf einen Schild, bricht er ihn, mit diesem Anteil der Wucht. */
    public static final double SMASH_VS_SHIELD = 1.0;

    /** Schildwall: Dauer · Stärke. */
    public static final int SHIELD_MS = 4_500;
    /** Rückstoß für den Angreifer am Schild, Vielfaches von RECOIL. */
    /** Abwehrchance am Schild: + DEFLECT, höchstens MAX. */
    public static final double SHIELD_DEFLECT = 0.42;
    public static final double SHIELD_DEFLECT_MAX = 0.95;
    public static final double SHIELD_RECOIL = 1.0;
    /** Der Schild verankert den Kreisel: Reibung · FRICTION (hilft auch gegen Gegner, die kaum angreifen). */
    public static final double SHIELD_FRICTION = 0.44;

    /** Ansturm: Dauer · Stärke. */
    public static final int RUSH_MS = 4_500;
    /** Clash-Rate und Angriffsgewicht während des Ansturms. */
    public static final double RUSH_RATE = 3.0;
    public static final double RUSH_WEIGHT = 4.0;
    /** Kürzerer Mindestabstand der Clashes im Ansturm. */
    public static final int RUSH_COOLDOWN_MS = 500;
    /** Intensität · (1 + BOOST · Stärke). */
    public static final double RUSH_BOOST = 0.3;
    /** Der Ansturm kostet eigenen Spin: Reibung · FRICTION. */
    public static final double RUSH_FRICTION = 1.5;

    /** Zweiter Wind: Spin zurück (· Stärke) … */
    public static final double SURGE_ENERGY = 0.073;
    /** … und so lange (· Stärke) nur dieser Anteil der Reibung. */
    public static final int SURGE_MS = 4_000;
    public static final double SURGE_FRICTION = 0.8;

    // ---------------------------------------------------------------- Optik (ohne Einfluss aufs Ergebnis)

    /** Mittlere Winkelgeschwindigkeit (rad/s), mit der der Treffpunkt um die Bowl wandert. */
    public static final double ORBIT_OMEGA = 2.0;
    /** Zufällige Abweichung des Treffpunkts (rad). */
    public static final double ORBIT_JITTER = 0.45;
    /** Ring-Out-Winkel streut um die Taschenmitte, als Anteil der halben Taschenbreite. */
    public static final double POCKET_SPREAD = 0.6;

    // ---------------------------------------------------------------- Hilfen

    /** Normalisiert einen Stat auf seinen Referenzwert und begrenzt auf [0, NORM_MAX]. */
    static double norm(int value, int ref) {
        return clamp(value / (double) ref, 0, NORM_MAX);
    }

    /** Gewicht linear auf [0, 1] zwischen leichtestem und schwerstem Teile-Build. */
    static double normWeight(int weight) {
        return clamp((weight - WEIGHT_MIN) / (double) (WEIGHT_MAX - WEIGHT_MIN), 0, NORM_MAX);
    }

    static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
