package org.example.controller;

import org.example.config.AuthInterceptor;
import org.example.dtos.TournamentDto;
import org.example.dtos.TournamentRequest;
import org.example.services.TournamentService;
import org.springframework.web.bind.annotation.*;

/** Solo-Cup gegen die KI. */
@RestController
@RequestMapping("/api/tournament")
public class TournamentController {

    private final TournamentService tournaments;

    public TournamentController(TournamentService tournaments) {
        this.tournaments = tournaments;
    }

    @PostMapping
    public TournamentDto start(@RequestAttribute(AuthInterceptor.USERNAME) String username,
                               @RequestBody TournamentRequest body) {
        return tournaments.start(username, body.beybladeId(), body.format());
    }

    @GetMapping
    public TournamentDto current(@RequestAttribute(AuthInterceptor.USERNAME) String username) {
        return tournaments.current(username);
    }

    @PostMapping("/next")
    public TournamentDto next(@RequestAttribute(AuthInterceptor.USERNAME) String username) {
        return tournaments.next(username);
    }
}
