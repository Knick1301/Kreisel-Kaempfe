package org.example.game;

public record BattleOutcome(Fighter winner, Fighter loser, Side winnerSide, FinishType finish,
                            int durationMs, BattleTimeline timeline) {}
