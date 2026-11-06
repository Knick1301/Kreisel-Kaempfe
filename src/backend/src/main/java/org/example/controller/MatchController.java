package org.example.controller;

import org.example.config.AuthInterceptor;
import org.example.dtos.LaunchRequest;
import org.example.dtos.MatchDto;
import org.example.dtos.MatchRequest;
import org.example.game.LaunchInput;
import org.example.services.MatchService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kampf mit Anspinnen: Match anlegen, Start abgeben, Stand abfragen. Für PvP kommt später nur ein
 * Matchmaking-Einstieg dazu, Anspinnen und Auswertung laufen über dieselben Endpunkte.
 */
@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final MatchService matches;

    public MatchController(MatchService matches) {
        this.matches = matches;
    }

    /** Neues Match gegen einen KI-Gegner (Gegner wird sofort gezogen). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MatchDto create(@RequestAttribute(AuthInterceptor.USERNAME) String username,
                           @RequestBody MatchRequest request) {
        return matches.createBotMatch(username, request.beybladeId(), request.arenaId(), request.variantId());
    }

    @GetMapping("/{id}")
    public MatchDto get(@RequestAttribute(AuthInterceptor.USERNAME) String username, @PathVariable String id) {
        return matches.get(id, username);
    }

    /** Eigenen Start abgeben. Ist der Gegner schon fertig, kommt der Kampfbericht direkt mit. */
    @PostMapping("/{id}/launch")
    public MatchDto launch(@RequestAttribute(AuthInterceptor.USERNAME) String username, @PathVariable String id,
                           @RequestBody LaunchRequest request) {
        return matches.launch(id, username, new LaunchInput(request.taps()));
    }
}
