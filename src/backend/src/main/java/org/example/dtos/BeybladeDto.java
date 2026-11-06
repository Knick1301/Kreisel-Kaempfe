package org.example.dtos;

import org.example.entities.Beyblade;

public record BeybladeDto(
        Long id,
        String name,
        PartDto fusionWheel,
        PartDto spinTrack,
        PartDto tip,
        BeybladeStats stats
) {
    public static BeybladeDto from(Beyblade b) {
        return new BeybladeDto(
                b.getId(), b.getName(),
                PartDto.from(b.getFusionWheel()),
                PartDto.from(b.getSpinTrack()),
                PartDto.from(b.getTip()),
                b.getStats());
    }
}
