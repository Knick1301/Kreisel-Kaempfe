package org.example.controller;

import org.example.config.AuthInterceptor;
import org.example.dtos.LaunchRequest;
import org.example.dtos.MatchDto;
import org.example.dtos.MatchRequest;
import org.example.dtos.UltRequest;
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
import org.springframework.web.server.ResponseStatusException;

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
        return matches.launch(id, username, new LaunchInput(request.taps(), request.aim(), request.pull(), request.speed()));
    }

    /** Ult auslösen: {@code t} = Kampfzeit beim Druck. Antwortet mit dem neu gerechneten Kampf. */
    @PostMapping("/{id}/ult")
    public MatchDto ult(@RequestAttribute(AuthInterceptor.USERNAME) String username, @PathVariable String id,
                        @RequestBody UltRequest request) {
        if (request == null || request.t() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zeitpunkt fehlt.");
        }
        return matches.ult(id, username, request.t());
    }

    /** Kampf zu Ende gesehen: Ergebnis werten und speichern. */
    @PostMapping("/{id}/finish")
    public MatchDto finish(@RequestAttribute(AuthInterceptor.USERNAME) String username, @PathVariable String id) {
        return matches.finish(id, username);
    }
}
