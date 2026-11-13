package org.example.dtos;

/** @param mode CLASSIC (Standard) oder ARENA */
public record ChallengeRequest(Long beybladeId, String mode) {}
