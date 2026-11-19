package org.example.controller;

import org.example.config.AuthInterceptor;
import org.example.dtos.AccountRequest;
import org.example.dtos.AuthDto;
import org.example.dtos.MeDto;
import org.example.entities.User;
import org.example.services.AuthService;
import org.example.services.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService users;
    private final AuthService auth;

    public AuthController(UserService users, AuthService auth) {
        this.users = users;
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDto register(@RequestBody AccountRequest request) {
        User user = users.register(request.username(), request.password());
        return new AuthDto(auth.issue(user), user.getUsername(),
                "Willkommen, " + user.getUsername() + "! Dein Konto wurde erstellt.");
    }

    @PostMapping("/login")
    public AuthDto login(@RequestBody AccountRequest request) {
        User user = users.login(request.username(), request.password());
        return new AuthDto(auth.issue(user), user.getUsername(), "Angemeldet als " + user.getUsername() + ".");
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestAttribute(AuthInterceptor.TOKEN) String token) {
        auth.revoke(token);
    }

    @GetMapping("/me")
    public MeDto me(@RequestAttribute(AuthInterceptor.USERNAME) String username) {
        return new MeDto(username);
    }
}
