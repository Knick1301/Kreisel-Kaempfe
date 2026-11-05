package org.example.game;

import java.util.Random;

public class Battle {

    private static final double BURST_CHANCE = 0.2;

    private final Arena arena;
    private final Fighter a;
    private final Fighter b;
    private final Random random;

    public Battle(Arena arena, Fighter a, Fighter b, Random random) {
        this.arena = arena;
        this.a = a;
        this.b = b;
        this.random = random;
    }

    public BattleOutcome run() {
        double energyA = a.startEnergy() * launch();
        double energyB = b.startEnergy() * launch();

        for (int round = 1; round <= arena.maxRounds(); round++) {
            double hitOnB = damage(a, b);
            double hitOnA = damage(b, a);

            boolean burstB = bursts(hitOnB, b);
            boolean burstA = bursts(hitOnA, a);
            if (burstA != burstB) {
                return burstB
                        ? new BattleOutcome(a, b, FinishType.BURST, round, (int) energyA)
                        : new BattleOutcome(b, a, FinishType.BURST, round, (int) energyB);
            }

            energyB -= hitOnB + arena.frictionLoss(b.stats().stamina());
            energyA -= hitOnA + arena.frictionLoss(a.stats().stamina());

            if (energyA <= 0 || energyB <= 0) {
                return energyA >= energyB
                        ? new BattleOutcome(a, b, FinishType.SPIN_OUT, round, (int) Math.max(0, energyA))
                        : new BattleOutcome(b, a, FinishType.SPIN_OUT, round, (int) Math.max(0, energyB));
            }
        }
        return energyA >= energyB
                ? new BattleOutcome(a, b, FinishType.DECISION, arena.maxRounds(), (int) energyA)
                : new BattleOutcome(b, a, FinishType.DECISION, arena.maxRounds(), (int) energyB);
    }

    private double launch() {
        return 0.7 + random.nextDouble() * 0.6;
    }

    private double damage(Fighter attacker, Fighter defender) {
        double hit = (attacker.stats().attack() * 0.9 + attacker.stats().dash() * 0.25)
                * (0.75 + random.nextDouble() * 0.5);
        double weightFactor = Math.sqrt(Math.max(1, attacker.stats().weight())
                / (double) Math.max(1, defender.stats().weight()));
        return Math.max(0.5, (hit - defender.stats().defense() * 0.8) * weightFactor);
    }

    private boolean bursts(double damage, Fighter defender) {
        double threshold = 3 + defender.stats().burstResistance() * 1.0;
        return damage > threshold && random.nextDouble() < BURST_CHANCE;
    }
}
