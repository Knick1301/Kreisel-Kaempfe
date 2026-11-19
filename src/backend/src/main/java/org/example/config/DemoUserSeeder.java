package org.example.config;

import org.example.entities.Beyblade;
import org.example.entities.User;
import org.example.repositories.AuthTokenRepository;
import org.example.repositories.BattleResultRepository;
import org.example.repositories.UserRepository;
import org.example.security.PasswordHasher;
import org.example.services.PartService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Order(2)
public class DemoUserSeeder implements CommandLineRunner {

    public static final String DEMO_USERNAME = "demo";

    /**
     * Früher geseedete Fake-Gegner. Sie werden einmalig entfernt, aber nur, wenn sie noch das geseedete
     * Passwort haben, damit ein echter Spieler mit gleichem Namen nie gelöscht wird.
     */
    private static final List<String> FORMER_FAKE_OPPONENTS = List.of("rivale", "wirbelwind", "titan");
    private static final String FORMER_FAKE_PASSWORD = "gegner123";

    private final UserRepository users;
    private final BattleResultRepository results;
    private final AuthTokenRepository tokens;
    private final PartService parts;
    private final PasswordHasher hasher;

    public DemoUserSeeder(UserRepository users, BattleResultRepository results, AuthTokenRepository tokens,
                          PartService parts, PasswordHasher hasher) {
        this.users = users;
        this.results = results;
        this.tokens = tokens;
        this.parts = parts;
        this.hasher = hasher;
    }

    @Override
    @Transactional
    public void run(String... args) {
        ensureUser(DEMO_USERNAME, "demo", "Beyblade1", 0, 0, 0);
        FORMER_FAKE_OPPONENTS.forEach(this::removeFakeOpponent);
    }

    private void removeFakeOpponent(String username) {
        users.findByUsername(username)
                .filter(u -> hasher.matches(FORMER_FAKE_PASSWORD, u.getPassword()))
                .ifPresent(u -> {
                    results.deleteAll(results.findForUser(username));
                    tokens.deleteByUser(u);
                    users.delete(u);
                });
    }

    private void ensureUser(String username, String password, String beybladeName, int wheel, int track, int tip) {
        User existing = users.findByUsername(username).orElse(null);
        if (existing != null) {
            if (!hasher.isHash(existing.getPassword())) {
                existing.setPassword(hasher.hash(existing.getPassword()));
                users.save(existing);
            }
            return;
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(hasher.hash(password));

        Beyblade beyblade = new Beyblade();
        beyblade.setName(beybladeName);
        beyblade.setFusionWheel(pick(parts.allFusionWheels(), wheel));
        beyblade.setSpinTrack(pick(parts.allSpinTracks(), track));
        beyblade.setTip(pick(parts.allTips(), tip));
        user.addBeyblade(beyblade);
        users.save(user);
    }

    private static <T> T pick(List<T> list, int index) {
        return list.get(Math.min(index, list.size() - 1));
    }
}
