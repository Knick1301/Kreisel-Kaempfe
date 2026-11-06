package org.example.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.example.entities.parts.FusionWheel;
import org.example.entities.parts.Part;
import org.example.game.UltType;

/** @param ult nur beim Fusion Wheel: dessen Ult */
@JsonInclude(JsonInclude.Include.NON_NULL)

public record PartDto(
        Long id,
        String name,
        String modelPath,
        int weight,
        int attack,
        int defense,
        int stamina,
        int dash,
        int burstResistance,
        UltType ult
) {
    public static PartDto from(Part p) {
        return new PartDto(
                p.getId(), p.getName(), p.getModelPath(), p.getWeight(),
                p.getAttack(), p.getDefense(), p.getStamina(),
                p.getDash(), p.getBurstResistance(),
                p instanceof FusionWheel w ? w.getUlt() : null);
    }
}