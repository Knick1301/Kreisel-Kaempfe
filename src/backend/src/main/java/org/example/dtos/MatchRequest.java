package org.example.dtos;

/**
 * @param arenaId   optional, ohne Angabe das Standard-Stadion
 * @param variantId optische Variante der Arena, ohne Angabe ihre Vorgabe
 */
public record MatchRequest(Long beybladeId, String arenaId, String variantId) {}
