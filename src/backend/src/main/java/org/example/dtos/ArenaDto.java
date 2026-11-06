package org.example.dtos;

import org.example.game.Arena;

import java.util.List;

/**
 * @param variant  optische Variante dieses Kampfs
 * @param variants alle Varianten der Arena (die erste ist die Vorgabe)
 */
public record ArenaDto(String id, String name, String variant, List<String> variants) {

    public static ArenaDto from(Arena arena) {
        return from(arena, null);
    }

    public static ArenaDto from(Arena arena, String variant) {
        return new ArenaDto(arena.id(), arena.name(), arena.variantOrDefault(variant), arena.variants());
    }
}
