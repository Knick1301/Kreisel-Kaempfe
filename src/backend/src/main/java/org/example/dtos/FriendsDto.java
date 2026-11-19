package org.example.dtos;

import java.util.List;

/**
 * Freundesliste aus Sicht eines Spielers.
 *
 * @param friends    angenommene Freunde
 * @param incoming   Anfragen an mich
 * @param outgoing   meine offenen Anfragen
 * @param challenges Herausforderungen an mich und von mir
 */
public record FriendsDto(List<Friend> friends, List<Friend> incoming, List<Friend> outgoing,
                         List<ChallengeDto> challenges) {

    /**
     * @param id           ID der Freundschaft (zum Annehmen, Ablehnen, Entfernen)
     * @param ranking      klassischer Rang
     * @param arenaRanking Arena-Rang, {@code null} ohne Arena-Kampf
     */
    public record Friend(Long id, String username, boolean online, RankDto ranking, RankDto arenaRanking) {}

    /**
     * @param mine    von mir ausgesprochen (sonst an mich)
     * @param mode    CLASSIC oder ARENA
     * @param matchId Match (CLASSIC) bzw. Raum (ARENA), sobald angenommen
     */
    public record ChallengeDto(String id, String from, String to, boolean mine, String mode, String matchId,
                               long expiresInMs) {}
}
