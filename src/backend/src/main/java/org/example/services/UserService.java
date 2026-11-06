package org.example.services;

import org.example.entities.User;
import org.example.repositories.UserRepository;
import org.example.security.PasswordHasher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Pattern;

@Service
public class UserService {

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_-]{3,20}$");
    public static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository users;
    private final PasswordHasher hasher;

    public UserService(UserRepository users, PasswordHasher hasher) {
        this.users = users;
        this.hasher = hasher;
    }

    @Transactional
    public User register(String username, String password) {
        String name = username == null ? "" : username.trim();
        if (!USERNAME.matcher(name).matches()) {
            throw badRequest("Der Benutzername muss 3–20 Zeichen lang sein (Buchstaben, Ziffern, _ und -).");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw badRequest("Das Passwort muss mindestens " + MIN_PASSWORD_LENGTH + " Zeichen lang sein.");
        }
        if (password.chars().noneMatch(Character::isLetter) || password.chars().noneMatch(Character::isDigit)) {
            throw badRequest("Das Passwort muss mindestens einen Buchstaben und eine Ziffer enthalten.");
        }
        if (users.findByUsername(name).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Der Benutzername \"" + name + "\" ist schon vergeben.");
        }
        User user = new User();
        user.setUsername(name);
        user.setPassword(hasher.hash(password));
        return users.save(user);
    }

    public User login(String username, String password) {
        return users.findByUsername(username == null ? "" : username.trim())
                .filter(u -> hasher.matches(password == null ? "" : password, u.getPassword()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Benutzername oder Passwort ist falsch."));
    }

    public User get(String username) {
        return users.findByUsername(username).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User " + username + " nicht gefunden"));
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
