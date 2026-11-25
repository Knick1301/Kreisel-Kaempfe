package org.example.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.entities.User;
import org.example.services.AuthService;
import org.example.services.PresenceService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String USERNAME = "auth.username";
    public static final String TOKEN = "auth.token";

    private final AuthService auth;
    private final PresenceService presence;

    public AuthInterceptor(AuthService auth, PresenceService presence) {
        this.auth = auth;
        this.presence = presence;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : null;
        User user = auth.authenticate(token).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bitte melde dich an."));
        request.setAttribute(USERNAME, user.getUsername());
        presence.seen(user.getUsername());
        request.setAttribute(TOKEN, token);
        return true;
    }
}
