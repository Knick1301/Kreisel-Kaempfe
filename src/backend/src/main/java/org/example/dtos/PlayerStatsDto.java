package org.example.dtos;

/**
 * @param wins         Siege gegen echte Spieler
 * @param rank         Platz in der Rangliste, {@code null} ohne gewerteten Kampf
 * @param botWins      Siege gegen KI-Gegner
 * @param ranking      Liga, Liga-Punkte und Level
 * @param arenaRanking Rang im Arena-Modus (WASD), {@code null} ohne Arena-Kampf
 * @param arenaRank    Platz in der Arena-Rangliste
 * @param cupsWon      gewonnene Solo-Cups
 */
public record PlayerStatsDto(String username, long wins, long losses, int winRate, Integer rank,
                             long botWins, long botLosses, RankDto ranking,
                             RankDto arenaRanking, long arenaWins, long arenaLosses, Integer arenaRank,
                             int cupsWon) {

    public PlayerStatsDto withArena(LeaderboardEntry arena) {
        if (arena == null) return this;
        return new PlayerStatsDto(username, wins, losses, winRate, rank, botWins, botLosses, ranking,
                arena.ranking(), arena.wins(), arena.losses(), arena.rank(), cupsWon);
    }
}
