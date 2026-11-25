package org.example.game;

import org.example.dtos.BeybladeStats;

/**
 * @param ult Ult des Fusion Wheels; ohne Angabe passend zum Stil (siehe {@link UltType#forArchetype})
 */
public record Fighter(String owner, String beyblade, BeybladeStats stats, UltType ult) {

    public Fighter {
        if (ult == null) ult = UltType.forArchetype(StyleVector.of(stats).archetype());
    }

    public Fighter(String owner, String beyblade, BeybladeStats stats) {
        this(owner, beyblade, stats, null);
    }
}
