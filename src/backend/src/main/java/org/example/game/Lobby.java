package org.example.game;

import java.time.Instant;

public class Lobby {

    private final String id;
    private final String name;
    private final String host;
    private final Long hostBeybladeId;
    private final Instant createdAt = Instant.now();
    private String guest;
    private Long guestBeybladeId;
    private LobbyStatus status = LobbyStatus.OPEN;
    private Long battleResultId;

    public Lobby(String id, String name, String host, Long hostBeybladeId) {
        this.id = id;
        this.name = name;
        this.host = host;
        this.hostBeybladeId = hostBeybladeId;
    }

    public void join(String guest, Long guestBeybladeId) {
        this.guest = guest;
        this.guestBeybladeId = guestBeybladeId;
        this.status = LobbyStatus.READY;
    }

    public void leaveGuest() {
        this.guest = null;
        this.guestBeybladeId = null;
        this.status = LobbyStatus.OPEN;
    }

    public void finish(Long battleResultId) {
        this.battleResultId = battleResultId;
        this.status = LobbyStatus.FINISHED;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getHost() { return host; }
    public Long getHostBeybladeId() { return hostBeybladeId; }
    public String getGuest() { return guest; }
    public Long getGuestBeybladeId() { return guestBeybladeId; }
    public LobbyStatus getStatus() { return status; }
    public Long getBattleResultId() { return battleResultId; }
    public Instant getCreatedAt() { return createdAt; }
}
