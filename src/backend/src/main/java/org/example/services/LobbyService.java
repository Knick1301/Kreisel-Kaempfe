package org.example.services;

import org.example.entities.BattleResult;
import org.example.entities.User;
import org.example.game.Lobby;
import org.example.game.LobbyStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LobbyService {

    private static final String ID_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int ID_LENGTH = 6;

    private final Map<String, Lobby> lobbies = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final UserService users;
    private final BeybladeService beyblades;
    private final BattleService battles;

    public LobbyService(UserService users, BeybladeService beyblades, BattleService battles) {
        this.users = users;
        this.beyblades = beyblades;
        this.battles = battles;
    }

    public synchronized Lobby create(String name, String username, Long beybladeId) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.length() < 3 || trimmed.length() > 30) {
            throw badRequest("Der Lobby-Name muss 3–30 Zeichen lang sein.");
        }
        User host = users.get(username);
        beyblades.owned(host, beybladeId);
        boolean hasActive = lobbies.values().stream()
                .anyMatch(l -> l.getHost().equals(username) && l.getStatus() != LobbyStatus.FINISHED);
        if (hasActive) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Du hast schon eine offene Lobby.");
        }
        Lobby lobby = new Lobby(newId(), trimmed, username, beybladeId);
        lobbies.put(lobby.getId(), lobby);
        return lobby;
    }

    public List<Lobby> open() {
        return lobbies.values().stream()
                .filter(l -> l.getStatus() == LobbyStatus.OPEN)
                .sorted(Comparator.comparing(Lobby::getCreatedAt).reversed())
                .toList();
    }

    public Lobby get(String id) {
        Lobby lobby = id == null ? null : lobbies.get(id.trim().toUpperCase());
        if (lobby == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lobby " + id + " nicht gefunden");
        }
        return lobby;
    }

    public synchronized Lobby join(String id, String username, Long beybladeId) {
        Lobby lobby = get(id);
        if (lobby.getHost().equals(username)) {
            throw badRequest("Du bist schon Host dieser Lobby.");
        }
        if (lobby.getStatus() != LobbyStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Die Lobby ist nicht mehr offen.");
        }
        beyblades.owned(users.get(username), beybladeId);
        lobby.join(username, beybladeId);
        return lobby;
    }

    public synchronized BattleResult start(String id, String username) {
        Lobby lobby = get(id);
        if (!lobby.getHost().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nur der Host kann den Kampf starten.");
        }
        if (lobby.getStatus() != LobbyStatus.READY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Es fehlt noch ein Mitspieler.");
        }
        User host = users.get(lobby.getHost());
        User guest = users.get(lobby.getGuest());
        BattleResult result = battles.fight(
                host, beyblades.owned(host, lobby.getHostBeybladeId()),
                guest, beyblades.owned(guest, lobby.getGuestBeybladeId()));
        lobby.finish(result.getId());
        return result;
    }

    public synchronized void leave(String id, String username) {
        Lobby lobby = get(id);
        if (lobby.getHost().equals(username)) {
            lobbies.remove(lobby.getId());
        } else if (username != null && username.equals(lobby.getGuest())
                && lobby.getStatus() == LobbyStatus.READY) {
            lobby.leaveGuest();
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Du bist nicht in dieser Lobby.");
        }
    }

    private String newId() {
        String id;
        do {
            StringBuilder sb = new StringBuilder(ID_LENGTH);
            for (int i = 0; i < ID_LENGTH; i++) {
                sb.append(ID_CHARS.charAt(random.nextInt(ID_CHARS.length())));
            }
            id = sb.toString();
        } while (lobbies.containsKey(id));
        return id;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
