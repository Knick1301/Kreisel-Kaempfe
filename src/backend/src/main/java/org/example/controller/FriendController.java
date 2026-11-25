package org.example.controller;

import org.example.config.AuthInterceptor;
import org.example.dtos.ChallengeRequest;
import org.example.dtos.FriendRequest;
import org.example.dtos.FriendsDto;
import org.example.services.FriendService;
import org.springframework.web.bind.annotation.*;

/** Freunde und direkte Herausforderungen. */
@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final FriendService friends;

    public FriendController(FriendService friends) {
        this.friends = friends;
    }

    @GetMapping
    public FriendsDto list(@RequestAttribute(AuthInterceptor.USERNAME) String username) {
        return friends.list(username);
    }

    @PostMapping
    public FriendsDto request(@RequestAttribute(AuthInterceptor.USERNAME) String username,
                              @RequestBody FriendRequest body) {
        return friends.request(username, body == null ? null : body.username());
    }

    @PostMapping("/{id}/accept")
    public FriendsDto accept(@RequestAttribute(AuthInterceptor.USERNAME) String username, @PathVariable Long id) {
        return friends.accept(username, id);
    }

    @DeleteMapping("/{id}")
    public FriendsDto remove(@RequestAttribute(AuthInterceptor.USERNAME) String username, @PathVariable Long id) {
        return friends.remove(username, id);
    }

    @PostMapping("/{name}/challenge")
    public FriendsDto challenge(@RequestAttribute(AuthInterceptor.USERNAME) String username,
                                @PathVariable String name, @RequestBody ChallengeRequest body) {
        return friends.challenge(username, name, body.beybladeId(), body.mode());
    }

    @PostMapping("/challenges/{id}/accept")
    public FriendsDto acceptChallenge(@RequestAttribute(AuthInterceptor.USERNAME) String username,
                                      @PathVariable String id, @RequestBody ChallengeRequest body) {
        return friends.acceptChallenge(username, id, body.beybladeId());
    }

    @DeleteMapping("/challenges/{id}")
    public FriendsDto declineChallenge(@RequestAttribute(AuthInterceptor.USERNAME) String username,
                                       @PathVariable String id) {
        return friends.declineChallenge(username, id);
    }
}
