package org.example.controller;

import org.example.config.AuthInterceptor;
import org.example.dtos.ArenaRoomDto;
import org.example.dtos.LaunchRequest;
import org.example.dtos.LeaderboardDto;
import org.example.dtos.MatchRequest;
import org.example.game.LaunchInput;
import org.example.services.ArenaRoomService;
import org.example.services.ArenaService;
import org.springframework.web.bind.annotation.*;

/**
 * Arena-Modus (Echtzeit-Physik auf dem Server): Raum gegen die KI anlegen, Raum abfragen, anspinnen, eigene
 * Rangliste. Der Kampf selbst läuft über den WebSocket {@code /ws/arena}.
 */
@RestController
@RequestMapping("/api/arena")
public class ArenaController {

    private final ArenaService arena;
    private final ArenaRoomService rooms;

    public ArenaController(ArenaService arena, ArenaRoomService rooms) {
        this.arena = arena;
        this.rooms = rooms;
    }

    @PostMapping("/rooms")
    public ArenaRoomDto create(@RequestAttribute(AuthInterceptor.USERNAME) String username,
                               @RequestBody MatchRequest request) {
        return rooms.createBot(username, request.beybladeId());
    }

    @GetMapping("/rooms/{id}")
    public ArenaRoomDto get(@RequestAttribute(AuthInterceptor.USERNAME) String username, @PathVariable String id) {
        return rooms.get(id, username);
    }

    @PostMapping("/rooms/{id}/launch")
    public ArenaRoomDto launch(@RequestAttribute(AuthInterceptor.USERNAME) String username, @PathVariable String id,
                               @RequestBody LaunchRequest request) {
        return rooms.launch(id, username,
                new LaunchInput(request.taps(), request.aim(), request.pull(), request.speed()));
    }

    @GetMapping("/leaderboard")
    public LeaderboardDto leaderboard(@RequestAttribute(AuthInterceptor.USERNAME) String username,
                                      @RequestParam(defaultValue = "25") int limit) {
        return arena.leaderboard(username, Math.clamp(limit, 1, 100));
    }
}
