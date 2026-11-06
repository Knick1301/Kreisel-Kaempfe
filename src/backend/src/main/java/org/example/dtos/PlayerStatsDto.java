package org.example.dtos;

/**
 * @param wins     Siege gegen echte Spieler (zählen für die Rangliste)
 * @param botWins  Siege gegen KI-Gegner (nur Profil)
 */
public record PlayerStatsDto(String username, long wins, long losses, int winRate, Integer rank,
                             long botWins, long botLosses) {}
