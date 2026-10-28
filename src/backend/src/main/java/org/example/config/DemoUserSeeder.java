package org.example.config;

import org.example.entities.Beyblade;
import org.example.entities.User;
import org.example.repositories.UserRepository;
import org.example.services.PartService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class DemoUserSeeder implements CommandLineRunner {

    public static final String DEMO_USERNAME = "demo";

    private final UserRepository users;
    private final PartService parts;

    public DemoUserSeeder(UserRepository users, PartService parts) {
        this.users = users;
        this.parts = parts;
    }

    @Override
    public void run(String... args) {
        if (users.findByUsername(DEMO_USERNAME).isPresent()) {
            return;
        }

        User user = new User();
        user.setUsername(DEMO_USERNAME);
        user.setPassword("demo");

        Beyblade beyblade = new Beyblade();
        beyblade.setName("Beyblade1");
        beyblade.setFusionWheel(parts.allFusionWheels().getFirst());
        beyblade.setSpinTrack(parts.allSpinTracks().getFirst());
        beyblade.setTip(parts.allTips().getFirst());
        user.addBeyblade(beyblade);

        users.save(user);
    }
}
