package org.example.dtos;

public record LeaderboardEntry(int rank, String username, long wins, long losses) {

    public long battles() {
        return wins + losses;
    }
}
