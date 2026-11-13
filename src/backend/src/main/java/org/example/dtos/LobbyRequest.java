package org.example.dtos;

/** @param mode CLASSIC (Standard) oder ARENA (Echtzeit-Physik), nur beim Erstellen */
public record LobbyRequest(String name, Long beybladeId, String mode) {}
