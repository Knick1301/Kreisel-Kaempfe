package org.example.dtos;

/** Spielersuche starten. @param mode CLASSIC (Standard) oder ARENA (Echtzeit-Physik) */
public record SearchRequest(Long beybladeId, String mode) {}
