package org.example.dtos;

import java.util.List;

public record LeaderboardDto(List<LeaderboardEntry> entries, LeaderboardEntry me, long totalPlayers,
                             boolean empty) {}
