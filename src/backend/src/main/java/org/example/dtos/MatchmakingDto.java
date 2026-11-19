package org.example.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Stand der Spielersuche.
 *
 * @param state    {@code WAITING} oder {@code FOUND}
 * @param waitedMs so lange wird schon gesucht
 * @param matchId  PvP-Match, sobald ein Gegner gefunden ist
 * @param waiting  Spieler, die gerade suchen (inklusive einem selbst)
 * @param mode     CLASSIC oder ARENA: Bei ARENA ist {@code matchId} ein Arena-Raum
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MatchmakingDto(String state, long waitedMs, String matchId, int waiting, String mode) {}
