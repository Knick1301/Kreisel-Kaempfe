package org.example.game;

public record Arena(String name, double friction, int maxRounds) {

    public static Arena standard() {
        return new Arena("Standard-Stadion", 8.0, 60);
    }

    public double frictionLoss(int stamina) {
        return Math.max(1.0, friction * (1.0 - stamina / 28.0));
    }
}
