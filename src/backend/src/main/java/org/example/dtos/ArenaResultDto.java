package org.example.dtos;

/** Verbuchtes Arena-Ergebnis: Änderung der Arena-LP und neuer Stand. */
public record ArenaResultDto(int ratingDelta, int xpGained, RankDto rank) {}
