package org.example.dtos;

import java.util.List;

/**
 * Arena-Raum (Echtzeit-Physik auf dem Server) aus Sicht eines Spielers.
 *
 * @param mode          BOT oder PVP
 * @param cup           Kampf aus dem Solo-Cup (ohne Liga-Punkte)
 * @param status        LAUNCH, COUNTDOWN, FIGHT oder DONE
 * @param youLaunched   eigener Start abgegeben
 * @param opponentReady Gegner hat angespinnt
 * @param expiresInMs   Restzeit fürs Anspinnen
 * @param result        Ausgang, sobald entschieden
 */
public record ArenaRoomDto(String id, String mode, boolean cup, String status, String yourSide, ArenaDto arena,
                           List<FighterDto> fighters, LaunchChallengeDto launch, boolean youLaunched,
                           boolean opponentReady, long expiresInMs, Result result) {

    /**
     * @param you eigene Wertung (LP, XP, neuer Arena-Rang), {@code null} bei der KI
     */
    public record Result(String winner, String finish, int durationMs, ArenaResultDto you) {}
}
