package org.example.controller;

import org.example.config.AuthInterceptor;
import org.example.dtos.LobbyDto;
import org.example.dtos.LobbyRequest;
import org.example.game.Lobby;
import org.example.services.LobbyService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/lobbies")
public class LobbyController {

    private final LobbyService lobbies;

    public LobbyController(LobbyService lobbies) {
        this.lobbies = lobbies;
    }

    @GetMapping
    public List<LobbyDto> open() {
        return lobbies.open().stream().map(this::toDto).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LobbyDto create(@RequestAttribute(AuthInterceptor.USERNAME) String username,
                           @RequestBody LobbyRequest request) {
        return toDto(lobbies.create(request.name(), username, request.beybladeId(),
                org.example.game.FightMode.parse(request.mode())));
    }

    @GetMapping("/{id}")
    public LobbyDto get(@PathVariable String id) {
        return toDto(lobbies.get(id));
    }

    @PostMapping("/{id}/join")
    public LobbyDto join(@RequestAttribute(AuthInterceptor.USERNAME) String username,
                         @PathVariable String id, @RequestBody LobbyRequest request) {
        return toDto(lobbies.join(id, username, request.beybladeId()));
    }

    /** Startet den Kampf: Die Lobby trägt danach die Match-ID, beide wechseln in den Kampf. */
    @PostMapping("/{id}/start")
    public LobbyDto start(@RequestAttribute(AuthInterceptor.USERNAME) String username,
                          @PathVariable String id) {
        return toDto(lobbies.start(id, username));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@RequestAttribute(AuthInterceptor.USERNAME) String username, @PathVariable String id) {
        lobbies.leave(id, username);
    }

    private LobbyDto toDto(Lobby lobby) {
        return LobbyDto.from(lobby);
    }
}
