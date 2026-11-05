package org.example.dtos;

public record PlayerStatsDto(String username, long wins, long losses, int winRate, Integer rank) {}
