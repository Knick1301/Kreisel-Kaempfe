package org.example.dtos;

import org.example.game.StyleVector;

/** Anteile am Stil (Summe 1), auf drei Nachkommastellen gerundet. */
public record StyleDto(double attack, double defense, double stamina) {

    public static StyleDto from(StyleVector v) {
        return new StyleDto(round(v.attack()), round(v.defense()), round(v.stamina()));
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
