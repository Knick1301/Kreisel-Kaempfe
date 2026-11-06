package org.example.dtos;

/** Eine Zeile der Rangliste. {@code wins}/{@code losses} zählen alle Kämpfe, auch gegen die KI. */
public record LeaderboardEntry(int rank, String username, long wins, long losses, RankDto ranking) {

    public long battles() {
        return wins + losses;
    }
}
