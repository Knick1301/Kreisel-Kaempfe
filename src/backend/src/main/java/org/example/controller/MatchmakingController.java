package org.example.controller;

import org.example.config.AuthInterceptor;
import org.example.dtos.SearchRequest;
import org.example.game.FightMode;
import org.example.dtos.MatchmakingDto;
import org.example.services.MatchmakingService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Spielersuche: anmelden, Stand abfragen (auch als Lebenszeichen), abbrechen. */
@RestController
@RequestMapping("/api/matchmaking")
public class MatchmakingController {

    private final MatchmakingService matchmaking;

    public MatchmakingController(MatchmakingService matchmaking) {
        this.matchmaking = matchmaking;
    }

    @PostMapping
    public MatchmakingDto join(@RequestAttribute(AuthInterceptor.USERNAME) String username,
                               @RequestBody SearchRequest request) {
        return matchmaking.join(username, request.beybladeId(), FightMode.parse(request.mode()));
    }

    @GetMapping
    public MatchmakingDto status(@RequestAttribute(AuthInterceptor.USERNAME) String username) {
        return matchmaking.status(username);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@RequestAttribute(AuthInterceptor.USERNAME) String username) {
        matchmaking.cancel(username);
    }
}
