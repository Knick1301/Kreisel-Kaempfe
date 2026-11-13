package org.example;

import org.example.dtos.ArenaRoomDto;
import org.example.dtos.BeybladeRequest;
import org.example.game.LaunchInput;
import org.example.services.ArenaRoomService;
import org.example.services.AuthService;
import org.example.services.BeybladeService;
import org.example.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Echter WebSocket gegen den laufenden Server: verbinden, Eingaben schicken, Stand bekommen. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ArenaSocketTest {

    @LocalServerPort int port;
    @Autowired ArenaRoomService rooms;
    @Autowired UserService users;
    @Autowired AuthService auth;
    @Autowired BeybladeService beyblades;

    @Test
    void stateKommtUeberDenSocket() throws Exception {
        String name = "ws" + UUID.randomUUID().toString().substring(0, 8);
        var user = users.register(name, "geheim123");
        Long bey = beyblades.create(name, new BeybladeRequest("Socket-Bey", 1L, 1L, 1L)).getId();
        String token = auth.issue(user);
        ArenaRoomDto room = rooms.createBot(name, bey);

        List<String> received = new CopyOnWriteArrayList<>();
        var handler = new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                received.add(message.getPayload());
            }
        };
        URI uri = URI.create("ws://localhost:" + port + "/ws/arena?room=" + room.id() + "&token=" + token);
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin("http://localhost:5174");
        WebSocketSession session = new StandardWebSocketClient()
                .execute(handler, headers, uri).get(5, TimeUnit.SECONDS);

        rooms.launch(room.id(), name, LaunchInput.NONE);
        session.sendMessage(new TextMessage("{\"type\":\"input\",\"x\":1,\"z\":0,\"dash\":false,\"ult\":false}"));

        long until = System.currentTimeMillis() + 6000;
        while (System.currentTimeMillis() < until
                && received.stream().noneMatch(m -> m.contains("\"status\":\"FIGHT\""))) {
            Thread.sleep(50);
        }
        session.close(CloseStatus.NORMAL);

        assertTrue(received.getFirst().contains("\"type\":\"room\""), "erst der Raum");
        assertTrue(received.stream().anyMatch(m -> m.contains("\"status\":\"COUNTDOWN\"")), "dann der Countdown");
        assertTrue(received.stream().anyMatch(m -> m.contains("\"status\":\"FIGHT\"") && m.contains("\"spin\"")),
                "dann der Kampf mit Stand");
    }

    @Test
    void ohneTokenWirdAbgewiesen() throws Exception {
        List<CloseStatus> closed = new CopyOnWriteArrayList<>();
        var handler = new TextWebSocketHandler() {
            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                closed.add(status);
            }
        };
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin("http://localhost:5174");
        new StandardWebSocketClient().execute(handler, headers,
                URI.create("ws://localhost:" + port + "/ws/arena?room=x&token=falsch")).get(5, TimeUnit.SECONDS);
        long until = System.currentTimeMillis() + 3000;
        while (closed.isEmpty() && System.currentTimeMillis() < until) Thread.sleep(20);
        assertTrue(!closed.isEmpty() && closed.getFirst().getCode() == CloseStatus.POLICY_VIOLATION.getCode());
    }
}
