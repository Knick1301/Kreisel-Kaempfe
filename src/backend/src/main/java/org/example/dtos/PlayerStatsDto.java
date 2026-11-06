package org.example.dtos;

/**
 * @param wins     Siege gegen echte Spieler
 * @param rank     Platz in der Rangliste, {@code null} ohne gewerteten Kampf
 * @param botWins  Siege gegen KI-Gegner
 * @param ranking  Liga, Liga-Punkte und Level
 */
public record PlayerStatsDto(String username, long wins, long losses, int winRate, Integer rank,
                             long botWins, long botLosses, RankDto ranking) {}
