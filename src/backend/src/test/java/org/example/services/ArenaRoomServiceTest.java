package org.example.services;

import org.example.dtos.ArenaRoomDto;
import org.example.dtos.BeybladeRequest;
import org.example.dtos.BeybladeStats;
import org.example.entities.FighterSnapshot;
import org.example.entities.User;
import org.example.game.LaunchInput;
import org.example.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Arena-Räume: Anspinnen, Countdown, Kampf auf dem Server, Wertung, Abbruch. Der Takt läuft von Hand. */
@SpringBootTest(properties = "arena.tick=false")
class ArenaRoomServiceTest {

    @Autowired ArenaRoomService rooms;
    @Autowired UserService users;
    @Autowired BeybladeService beyblades;
    @Autowired UserRepository userRepo;

    private String name() {
        return "ar" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Long player(String name) {
        users.register(name, "geheim123");
        return beyblades.create(name, new BeybladeRequest("Bey " + name, 1L, 1L, 1L)).getId();
    }

    /** Verbindung, die alle Nachrichten mitschreibt. */
    private static WebSocketSession socket(List<String> sink) throws Exception {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.isOpen()).thenReturn(true);
        doAnswer(inv -> {
            sink.add(((TextMessage) inv.getArgument(0)).getPayload());
            return null;
        }).when(s).sendMessage(any());
        return s;
    }

    /** Raum mit künstlicher Zeit bis zum Ende laufen lassen. */
    private long runToEnd(ArenaRoomService.Room room, long start) {
        long t = start;
        for (int i = 0; i < 6000 && room.status != ArenaRoomService.Status.DONE; i++) {
            t += 16;
            synchronized (room) {
                rooms.tick(room, t);
            }
        }
        return t;
    }

    @Test
    void kampfGegenKiLaeuftAufDemServerUndWirdEinmalGewertet() throws Exception {
        String me = name();
        Long bey = player(me);
        ArenaRoomDto dto = rooms.createBot(me, bey);
        assertEquals("LAUNCH", dto.status());
        assertTrue(dto.opponentReady(), "die KI spinnt sofort an");
        assertEquals("A", dto.yourSide());

        List<String> sent = new ArrayList<>();
        rooms.attach(dto.id(), me, socket(sent));
        assertTrue(sent.getFirst().contains("\"type\":\"room\""));

        dto = rooms.launch(dto.id(), me, LaunchInput.NONE);
        assertEquals("COUNTDOWN", dto.status());

        ArenaRoomService.Room room = rooms.room(dto.id());
        long start = ArenaRoomService.now() + ArenaRoomService.COUNTDOWN_MS;
        // eigene Eingabe: auf den Gegner zu dashen
        rooms.input(dto.id(), me, 1, 0, true, false);
        runToEnd(room, start);

        assertEquals(ArenaRoomService.Status.DONE, room.status);
        assertTrue(sent.stream().anyMatch(m -> m.contains("\"type\":\"state\"") && m.contains("\"status\":\"FIGHT\"")));
        dto = rooms.get(dto.id(), me);
        assertNotNull(dto.result());
        assertNotNull(dto.result().you());

        User u = userRepo.findByUsername(me).orElseThrow();
        int fights = (u.getArenaWins() == null ? 0 : u.getArenaWins()) + (u.getArenaLosses() == null ? 0 : u.getArenaLosses());
        assertEquals(1, fights);
        // ein zweiter Takt wertet nicht nochmal
        synchronized (room) {
            rooms.tick(room, ArenaRoomService.now() + 100_000);
        }
        u = userRepo.findByUsername(me).orElseThrow();
        assertEquals(1, (u.getArenaWins() == null ? 0 : u.getArenaWins()) + (u.getArenaLosses() == null ? 0 : u.getArenaLosses()));
    }

    @Test
    void ohneVerbindungImKampfVerliertMan() {
        String me = name();
        Long bey = player(me);
        ArenaRoomDto dto = rooms.launch(rooms.createBot(me, bey).id(), me, LaunchInput.NONE);
        ArenaRoomService.Room room = rooms.room(dto.id());
        long t = ArenaRoomService.now() + ArenaRoomService.COUNTDOWN_MS + 1;
        synchronized (room) {
            rooms.tick(room, t);
        }
        assertEquals(ArenaRoomService.Status.FIGHT, room.status);
        synchronized (room) {
            rooms.tick(room, t + ArenaRoomService.DISCONNECT_MS + 100);
        }
        assertEquals(ArenaRoomService.Status.DONE, room.status);
        assertEquals("B", rooms.get(dto.id(), me).result().winner());
    }

    @Test
    void pvpRaumBeideSpinnenAnUndSteuern() throws Exception {
        String a = name();
        String b = name();
        Long beyA = player(a);
        Long beyB = player(b);
        String id = rooms.createPvp(a, beyA, b, beyB);
        assertEquals("B", rooms.get(id, b).yourSide());
        assertEquals("LAUNCH", rooms.launch(id, a, LaunchInput.NONE).status());
        assertFalse(rooms.get(id, b).youLaunched());
        assertEquals("COUNTDOWN", rooms.launch(id, b, LaunchInput.NONE).status());

        List<String> sentA = new ArrayList<>();
        List<String> sentB = new ArrayList<>();
        rooms.attach(id, a, socket(sentA));
        rooms.attach(id, b, socket(sentB));
        rooms.input(id, a, 1, 0, false, false);
        rooms.input(id, b, -1, 0, false, false);
        ArenaRoomService.Room room = rooms.room(id);
        runToEnd(room, ArenaRoomService.now() + ArenaRoomService.COUNTDOWN_MS);

        assertEquals(ArenaRoomService.Status.DONE, room.status);
        // beide bekommen Stand und Ergebnis, beide werden gewertet (volle Elo)
        assertTrue(sentA.stream().anyMatch(m -> m.contains("\"type\":\"state\"")));
        assertTrue(sentB.stream().anyMatch(m -> m.contains("\"type\":\"state\"")));
        var resA = rooms.get(id, a).result();
        var resB = rooms.get(id, b).result();
        assertEquals(resA.winner(), resB.winner());
        // Sieger gewinnt LP, der Verlierer verliert (unter den Startwert fällt niemand, deshalb ≤ 0)
        var winner = resA.winner().equals("A") ? resA : resB;
        var loser = winner == resA ? resB : resA;
        assertTrue(winner.you().ratingDelta() > 0);
        assertTrue(loser.you().ratingDelta() <= 0);
    }

    @Test
    void cupRaumOhneLigaPunkte() {
        String me = name();
        Long bey = player(me);
        String id = rooms.createCup(me, bey, FighterSnapshot.bot("KI", "Test-Bot",
                new BeybladeStats(5, 5, 8, 5, 78, 3), "#8fb4ff"), 0.5, 160);
        rooms.launch(id, me, LaunchInput.NONE);
        ArenaRoomService.Room room = rooms.room(id);
        long t = ArenaRoomService.now() + ArenaRoomService.COUNTDOWN_MS + 1;
        synchronized (room) {
            rooms.tick(room, t);
            rooms.tick(room, t + ArenaRoomService.DISCONNECT_MS + 100);
        }
        assertEquals(0, rooms.get(id, me).result().you().ratingDelta());
        assertNotNull(rooms.winnerIfDone(id));
    }

}
