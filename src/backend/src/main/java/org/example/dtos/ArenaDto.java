package org.example.dtos;

import org.example.game.Arena;

public record ArenaDto(String id, String name) {

    public static ArenaDto from(Arena arena) {
        return new ArenaDto(arena.id(), arena.name());
    }
}
