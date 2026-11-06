package org.example.controller;

import org.example.config.AuthInterceptor;
import org.example.dtos.BattleReportDto;
import org.example.dtos.BeybladeDto;
import org.example.dtos.BeybladeRequest;
import org.example.dtos.PlayerStatsDto;
import org.example.services.BattleService;
import org.example.services.BeybladeService;
import org.example.services.LeaderboardService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/me")
public class UserController {

    private final BeybladeService beyblades;
    private final LeaderboardService leaderboard;
    private final BattleService battles;

    public UserController(BeybladeService beyblades, LeaderboardService leaderboard, BattleService battles) {
        this.beyblades = beyblades;
        this.leaderboard = leaderboard;
        this.battles = battles;
    }

    @GetMapping("/stats")
    public PlayerStatsDto stats(@RequestAttribute(AuthInterceptor.USERNAME) String username) {
        return leaderboard.stats(username);
    }

    @GetMapping("/battles")
    public List<BattleReportDto> battles(@RequestAttribute(AuthInterceptor.USERNAME) String username,
                                         @RequestParam(defaultValue = "10") int limit) {
        return battles.history(username, Math.clamp(limit, 1, 50)).stream().map(BattleReportDto::from).toList();
    }

    @GetMapping("/beyblades")
    public List<BeybladeDto> beyblades(@RequestAttribute(AuthInterceptor.USERNAME) String username) {
        return beyblades.forUser(username).stream().map(BeybladeDto::from).toList();
    }

    @PostMapping("/beyblades")
    @ResponseStatus(HttpStatus.CREATED)
    public BeybladeDto create(@RequestAttribute(AuthInterceptor.USERNAME) String username,
                              @RequestBody BeybladeRequest request) {
        return BeybladeDto.from(beyblades.create(username, request));
    }

    @PutMapping("/beyblades/{id}")
    public BeybladeDto update(@RequestAttribute(AuthInterceptor.USERNAME) String username,
                              @PathVariable Long id, @RequestBody BeybladeRequest request) {
        return BeybladeDto.from(beyblades.update(username, id, request));
    }

    @DeleteMapping("/beyblades/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute(AuthInterceptor.USERNAME) String username, @PathVariable Long id) {
        beyblades.delete(username, id);
    }
}
