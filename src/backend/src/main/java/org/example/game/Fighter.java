package org.example.game;

import org.example.dtos.BeybladeStats;

public record Fighter(String owner, String beyblade, BeybladeStats stats) {

    double startEnergy() {
        return 100 + stats.stamina() * 3.0 + stats.weight() * 0.3;
    }
}
