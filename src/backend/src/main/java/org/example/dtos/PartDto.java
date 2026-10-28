package org.example.dtos;

import org.example.entities.parts.Part;

public record PartDto(
        Long id,
        String name,
        String modelPath,
        int weight,
        int attack,
        int defense,
        int stamina,
        int dash,
        int burstResistance
) {
    public static PartDto from(Part p) {
        return new PartDto(
                p.getId(), p.getName(), p.getModelPath(), p.getWeight(),
                p.getAttack(), p.getDefense(), p.getStamina(),
                p.getDash(), p.getBurstResistance());
    }
}