package org.example.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.entities.User;
import org.example.services.ArenaRoomService;
import org.example.services.AuthService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;

/**
 * WebSocket der Arena-Räume: {@code /ws/arena?room=…&token=…}. Der Browser schickt seine Eingaben
 * ({@code {"type":"input","x":…,"z":…,"dash":…,"ult":…}}), der Server den Stand.
 */
@Component
public class ArenaSocketHandler extends TextWebSocketHandler {

    private static final String ROOM = "arena.room";
    private static final String USER = "arena.user";

    private final ArenaRoomService rooms;
    private final AuthService auth;
    private final ObjectMapper json;

    public ArenaSocketHandler(ArenaRoomService rooms, AuthService auth, ObjectMapper json) {
        this.rooms = rooms;
        this.auth = auth;
        this.json = json;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        var params = UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams();
        String room = params.getFirst("room");
        Optional<User> user = auth.authenticate(params.getFirst("token"));
        if (room == null || user.isEmpty()) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Bitte melde dich an."));
            return;
        }
        try {
            rooms.attach(room, user.get().getUsername(), session);
        } catch (RuntimeException e) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Raum nicht gefunden."));
            return;
        }
        session.getAttributes().put(ROOM, room);
        session.getAttributes().put(USER, user.get().getUsername());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String room = (String) session.getAttributes().get(ROOM);
        String user = (String) session.getAttributes().get(USER);
        if (room == null || user == null) return;
        JsonNode msg = json.readTree(message.getPayload());
        if (!"input".equals(msg.path("type").asText())) return;
        rooms.input(room, user, msg.path("x").asDouble(0), msg.path("z").asDouble(0),
                msg.path("dash").asBoolean(false), msg.path("ult").asBoolean(false));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String room = (String) session.getAttributes().get(ROOM);
        if (room != null) rooms.detach(room, session);
    }
}
