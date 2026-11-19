package org.example.dtos;

import java.util.List;

/**
 * Solo-Cup aus Sicht des Spielers.
 *
 * @param format    CLASSIC oder ARENA
 * @param status    RUNNING, WON oder OUT
 * @param round     aktuelle Runde 0–2 (= Zahl der fertigen Runden)
 * @param roundName Viertelfinale, Halbfinale, Finale; {@code null} wenn vorbei
 * @param results   fertige Runden mit Siegern
 * @param upcoming  Paarungen der aktuellen Runde
 * @param opponent  nächster Gegner, {@code null} wenn vorbei
 * @param matchId   laufender eigener Kampf, {@code null} vor „Nächster Kampf“
 * @param xpReward  Bonus-XP für den Cup-Sieg (nur bei WON)
 */
public record TournamentDto(String id, String format, String status, int round, String roundName,
                            List<List<CupPairing>> results, List<CupPairing> upcoming, FighterDto opponent,
                            String matchId, int xpReward) {

    /**
     * @param slot     Platz im Baum 0–7
     * @param beyblade Name des Kreisels
     * @param username Spieler, {@code null} bei KI
     * @param level    Stufe der KI 0–2, {@code null} beim Spieler
     */
    public record CupEntry(int slot, String beyblade, String username, boolean you, Integer level) {}

    /** @param winner "A" oder "B", {@code null} solange offen */
    public record CupPairing(CupEntry a, CupEntry b, String winner, String finish) {}
}
