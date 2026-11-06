package org.example.controller;

import org.example.config.AuthInterceptor;
import org.example.dtos.BattleReportDto;
import org.example.dtos.BotMatchRequest;
import org.example.dtos.LeaderboardDto;
import org.example.dtos.QuickMatchRequest;
import org.example.services.BattleService;
import org.example.services.LeaderboardService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BattleController {

    private final BattleService battles;
    private final LeaderboardService leaderboard;

    public BattleController(BattleService battles, LeaderboardService leaderboard) {
        this.battles = battles;
        this.leaderboard = leaderboard;
    }

    @PostMapping("/battles/quick")
    @ResponseStatus(HttpStatus.CREATED)
    public BattleReportDto quickMatch(@RequestAttribute(AuthInterceptor.USERNAME) String username,
                                      @RequestBody QuickMatchRequest request) {
        return battles.report(battles.quickMatch(username, request.beybladeId()));
    }

    /** Kampf gegen einen KI-Gegner mit Zufallsname und Zufallswerten. */
    @PostMapping("/battles/bot")
    @ResponseStatus(HttpStatus.CREATED)
    public BattleReportDto botMatch(@RequestAttribute(AuthInterceptor.USERNAME) String username,
                                    @RequestBody BotMatchRequest request) {
        return battles.report(battles.botMatch(username, request.beybladeId(), request.arenaId(), request.variantId()));
    }

    /** Einzelner Kampf inklusive Timeline, wird per Seed neu simuliert (Replay). */
    @GetMapping("/battles/{id}")
    public BattleReportDto battle(@PathVariable Long id) {
        return battles.report(id);
    }

    @GetMapping("/leaderboard")
    public LeaderboardDto leaderboard(@RequestAttribute(AuthInterceptor.USERNAME) String username,
                                      @RequestParam(defaultValue = "10") int limit) {
        return leaderboard.leaderboard(username, Math.clamp(limit, 1, 100));
    }
}
