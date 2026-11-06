package org.example.services;

import org.example.entities.AuthToken;
import org.example.entities.User;
import org.example.repositories.AuthTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class AuthService {

    public static final Duration SESSION_LIFETIME = Duration.ofDays(30);

    private final AuthTokenRepository tokens;
    private final SecureRandom random = new SecureRandom();

    public AuthService(AuthTokenRepository tokens) {
        this.tokens = tokens;
    }

    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new AuthToken(hash(raw), user, Instant.now().plus(SESSION_LIFETIME)));
        return raw;
    }

    @Transactional(readOnly = true)
    public Optional<User> authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return Optional.empty();
        return tokens.findByTokenHash(hash(rawToken))
                .filter(t -> !t.isExpired())
                .map(AuthToken::getUser);
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null) return;
        tokens.findByTokenHash(hash(rawToken)).ifPresent(tokens::delete);
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
