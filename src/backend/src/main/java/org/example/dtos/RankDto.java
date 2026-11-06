package org.example.dtos;

/**
 * Liga-Stand eines Spielers.
 *
 * @param tier          Liga ohne Division, z. B. "Silber"
 * @param division      1–3 (III ist die unterste), {@code null} in der Meister-Liga
 * @param league        Anzeigename, z. B. "Silber II"
 * @param divisionFloor Rating, ab dem die aktuelle Division beginnt
 * @param progress      Fortschritt in der Division, 0–100
 * @param nextLeague    nächste Division, {@code null} in der Meister-Liga
 * @param pointsToNext  fehlende Punkte bis dahin
 * @param xpLevelStart  XP, ab denen das aktuelle Level beginnt
 * @param xpNextLevel   XP für das nächste Level
 */
public record RankDto(int rating, String tier, Integer division, String league, int divisionFloor, int progress,
                      String nextLeague, Integer pointsToNext, int level, int xp, int xpLevelStart,
                      int xpNextLevel) {}
