package org.example.config;

import org.example.entities.User;
import org.example.repositories.UserRepository;
import org.example.security.PasswordHasher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Legt den System-User für KI-Gegner an. Der Name ist kürzer als bei der Registrierung erlaubt
 * (3–20 Zeichen), kein Mensch kann ihn also belegen. Das Passwort ist ein Hash eines zufälligen Geheimnisses,
 * das nirgends gespeichert wird: Anmelden ist unmöglich.
 */
@Component
@Order(3)
public class SystemUserSeeder implements CommandLineRunner {

    public static final String BOT_USERNAME = "KI";

    private final UserRepository users;
    private final PasswordHasher hasher;

    public SystemUserSeeder(UserRepository users, PasswordHasher hasher) {
        this.users = users;
        this.hasher = hasher;
    }

    @Override
    public void run(String... args) {
        if (users.findByUsername(BOT_USERNAME).isPresent()) return;
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        User bot = new User();
        bot.setUsername(BOT_USERNAME);
        bot.setPassword(hasher.hash(HexFormat.of().formatHex(secret)));
        users.save(bot);
    }
}
