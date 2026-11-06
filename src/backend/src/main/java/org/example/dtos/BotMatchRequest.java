package org.example.dtos;

/** @param arenaId optional, ohne Angabe das Standard-Stadion */
public record BotMatchRequest(Long beybladeId, String arenaId) {}
