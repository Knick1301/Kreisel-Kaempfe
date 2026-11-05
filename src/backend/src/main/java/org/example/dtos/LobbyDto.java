package org.example.dtos;

import org.example.game.Lobby;
import org.example.game.LobbyStatus;

import java.time.Instant;

public record LobbyDto(String id, String name, String host, String guest, LobbyStatus status,
                       Instant createdAt, BattleReportDto result) {

    public static LobbyDto from(Lobby l, BattleReportDto result) {
        return new LobbyDto(l.getId(), l.getName(), l.getHost(), l.getGuest(), l.getStatus(),
                l.getCreatedAt(), result);
    }
}
