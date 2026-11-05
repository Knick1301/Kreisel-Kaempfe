package org.example.game;

public record BattleOutcome(Fighter winner, Fighter loser, FinishType finish, int rounds,
                            int winnerEnergyLeft) {}
