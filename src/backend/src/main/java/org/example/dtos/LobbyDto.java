package org.example.dtos;

import org.example.game.Lobby;
import org.example.game.LobbyStatus;

import java.time.Instant;

/** @param matchId PvP-Match, sobald der Host gestartet hat: beide Spieler wechseln in den Kampf */
public record LobbyDto(String id, String name, String host, String guest, LobbyStatus status,
                       Instant createdAt, String matchId, String mode) {

    public static LobbyDto from(Lobby l) {
        return new LobbyDto(l.getId(), l.getName(), l.getHost(), l.getGuest(), l.getStatus(),
                l.getCreatedAt(), l.getMatchId(), l.getMode().name());
    }
}
