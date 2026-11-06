package org.example.game;

import org.example.dtos.BeybladeStats;

import java.util.List;
import java.util.Random;

import static org.example.game.BattleTuning.*;

/**
 * Baut KI-Gegner mit zufälligen Werten: gleiches Stat-Budget wie der Spieler (±10 %), zufällige Gewichtung
 * mit leichtem Archetyp-Bias, alle Werte in dem Bereich, der auch mit echten Teilen erreichbar ist.
 */
public final class BotFactory {

    private BotFactory() {}

    public record Bot(String name, BeybladeStats stats, String accent) {}

    // Nur maskuline Substantive, damit die starke Adjektivendung „-er“ immer stimmt.
    private static final List<String> ADJECTIVES = List.of(
            "Eisiger", "Glühender", "Stählerner", "Wilder", "Stiller", "Donnernder", "Flinker", "Dunkler",
            "Goldener", "Rasender", "Silberner", "Tosender", "Schwarzer", "Funkelnder", "Grimmiger", "Kalter",
            "Roter", "Ewiger", "Heulender", "Lodernder", "Eherner", "Wirbelnder", "Stolzer", "Blinder");
    private static final List<String> NOUNS = List.of(
            "Orkan", "Sturm", "Falke", "Wirbel", "Titan", "Komet", "Drache", "Phönix", "Blitz", "Greif",
            "Taifun", "Zyklon", "Meteor", "Wächter", "Wolf", "Kreisel", "Hammer", "Mond", "Nebel", "Rabe",
            "Löwe", "Tornado", "Gigant", "Schatten");
    private static final List<String> ACCENTS = List.of(
            "#8fb4ff", "#ff8f6b", "#9be3a8", "#f2c46d", "#c49bff", "#6fd6e8", "#ff9bc2", "#d9e36f");

    private static final int[] MIN = {ATTACK_MIN, DEFENSE_MIN, STAMINA_MIN};
    private static final int[] MAX = {ATTACK_REF, DEFENSE_REF, STAMINA_REF};

    public static Bot create(BeybladeStats player, Random rng) {
        double playerBudget = StyleVector.of(player).budget();
        double target = clamp(playerBudget * (1 + BOT_BUDGET_SPREAD * (2 * rng.nextDouble() - 1)),
                normSum(MIN), normSum(MAX));

        double[] weights = new double[3];
        for (int i = 0; i < 3; i++) weights[i] = 1 + 0.8 * rng.nextDouble();
        if (rng.nextDouble() >= BOT_BALANCE_CHANCE) {
            weights[rng.nextInt(3)] += 0.8 + 0.8 * rng.nextDouble();
        }

        int[] core = distribute(target, weights);
        correctBudget(core, weights, playerBudget);

        double total = norm(core[0], 0) + norm(core[1], 1) + norm(core[2], 2);
        double sa = norm(core[0], 0) / total;
        double sd = norm(core[1], 1) / total;
        double ss = norm(core[2], 2) / total;
        double third = 1 / 3.0;

        int dash = scale(DASH_MIN, DASH_REF,
                0.5 + 1.4 * (sa - third) - 0.4 * (sd - third) + 0.35 * (rng.nextDouble() - 0.5));
        int weight = scale(WEIGHT_MIN, WEIGHT_MAX,
                0.45 + 1.2 * (sd - third) - 0.9 * (ss - third) + 0.35 * (rng.nextDouble() - 0.5));
        int burst = scale(BURST_MIN, BURST_REF,
                0.5 + 1.0 * (sd - third) - 0.6 * (ss - third) + 0.5 * (rng.nextDouble() - 0.5));

        String name = ADJECTIVES.get(rng.nextInt(ADJECTIVES.size())) + " " + NOUNS.get(rng.nextInt(NOUNS.size()));
        String accent = ACCENTS.get(rng.nextInt(ACCENTS.size()));
        return new Bot(name, new BeybladeStats(core[0], core[1], core[2], dash, weight, burst), accent);
    }

    /** Verteilt das Budget nach Gewichten, Werte über dem Maximum geben ihren Überschuss an die anderen ab. */
    private static int[] distribute(double target, double[] weights) {
        double[] x = new double[3];
        boolean[] fixed = new boolean[3];
        double remaining = target;
        for (int round = 0; round < 3; round++) {
            double free = 0;
            for (int i = 0; i < 3; i++) if (!fixed[i]) free += weights[i];
            boolean changed = false;
            for (int i = 0; i < 3; i++) {
                if (fixed[i]) continue;
                x[i] = remaining * weights[i] / free;
                double lo = MIN[i] / (double) MAX[i];
                if (x[i] > 1 || x[i] < lo) {
                    x[i] = clamp(x[i], lo, 1);
                    fixed[i] = true;
                    remaining -= x[i];
                    changed = true;
                }
            }
            if (!changed) break;
        }
        int[] raw = new int[3];
        for (int i = 0; i < 3; i++) raw[i] = (int) clamp(Math.round(x[i] * MAX[i]), MIN[i], MAX[i]);
        return raw;
    }

    /** Rundung kann das Budget verschieben, notfalls einzelne Punkte nachjustieren. */
    private static void correctBudget(int[] core, double[] weights, double playerBudget) {
        for (int guard = 0; guard < 40; guard++) {
            double budget = norm(core[0], 0) + norm(core[1], 1) + norm(core[2], 2);
            int step;
            if (budget < playerBudget * 0.91) step = 1;
            else if (budget > playerBudget * 1.09) step = -1;
            else return;
            int best = -1;
            for (int i = 0; i < 3; i++) {
                int next = core[i] + step;
                if (next < MIN[i] || next > MAX[i]) continue;
                if (best < 0 || (step > 0 ? weights[i] > weights[best] : weights[i] < weights[best])) best = i;
            }
            if (best < 0) return;
            core[best] += step;
        }
    }

    private static double norm(int value, int index) {
        return value / (double) MAX[index];
    }

    private static double normSum(int[] values) {
        return norm(values[0], 0) + norm(values[1], 1) + norm(values[2], 2);
    }

    private static int scale(int min, int max, double t) {
        return (int) Math.round(min + clamp(t, 0, 1) * (max - min));
    }
}
