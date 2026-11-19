package org.example.dtos;

/** @param format CLASSIC (Standard) oder ARENA */
public record TournamentRequest(Long beybladeId, String format) {}
