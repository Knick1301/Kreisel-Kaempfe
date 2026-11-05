package org.example.config;

import org.example.entities.Beyblade;
import org.example.entities.User;
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

    private record Opponent(String username, String beyblade, int wheel, int track, int tip) {}

    private static final List<Opponent> OPPONENTS = List.of(
            new Opponent("rivale", "Bollwerk-Kern", 1, 2, 2),
            new Opponent("wirbelwind", "Wirbelsturm", 2, 1, 1),
            new Opponent("titan", "Schwerer Titan", 0, 0, 2));

    private final UserRepository users;
    private final PartService parts;
    private final PasswordHasher hasher;

    public DemoUserSeeder(UserRepository users, PartService parts, PasswordHasher hasher) {
        this.users = users;
        this.parts = parts;
        this.hasher = hasher;
    }

    @Override
    @Transactional
    public void run(String... args) {
        ensureUser(DEMO_USERNAME, "demo", "Beyblade1", 0, 0, 0);
        for (Opponent o : OPPONENTS) {
            ensureUser(o.username(), "gegner123", o.beyblade(), o.wheel(), o.track(), o.tip());
        }
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
