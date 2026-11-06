package org.example.services;

import org.example.config.SystemUserSeeder;
import org.example.dtos.RankDto;
import org.example.entities.BattleResult;
import org.example.entities.User;
import org.example.game.BattleMode;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Liga-Punkte (Elo) und Level.
 *
 * <ul>
 *   <li>PvP: volle Elo-Änderung für beide Spieler.</li>
 *   <li>KI: Der Bot hat das Rating des Spielers, die Änderung zählt ×{@value #BOT_FACTOR} und nur unterhalb
 *       von Gold ({@value #BOT_RATING_CAP}). Darüber geben KI-Kämpfe keine Liga-Punkte mehr, aber weiter XP.</li>
 *   <li>XP: Sieg {@value #XP_WIN}, Niederlage {@value #XP_LOSS}, PvP doppelt.</li>
 *   <li>Unter den Startwert (Bronze III) fällt niemand, die unterste Stufe kann man nicht verlieren.</li>
 * </ul>
 */
@Service
public class RankingService {

    public static final int START_RATING = 950;
    public static final int K = 32;
    public static final double BOT_FACTOR = 0.4;
    public static final int BOT_RATING_CAP = 1250;
    public static final int XP_WIN = 50;
    public static final int XP_LOSS = 20;
    public static final int XP_PER_LEVEL_STEP = 50;

    /** Ligen mit Startwert. Jede Liga ist {@value #TIER_WIDTH} Punkte breit und hat drei Divisionen. */
    record Tier(String name, int floor) {}

    static final int TIER_WIDTH = 150;
    static final int DIVISION_WIDTH = TIER_WIDTH / 3;
    static final List<Tier> TIERS = List.of(
            new Tier("Bronze", START_RATING),
            new Tier("Silber", 1100),
            new Tier("Gold", 1250),
            new Tier("Platin", 1400),
            new Tier("Diamant", 1550),
            new Tier("Meister", 1700));
    private static final String[] DIVISION_NAMES = {"", "I", "II", "III"};

    /** Erwarteter Punktanteil von A gegen B (0–1). */
    public static double expected(int ratingA, int ratingB) {
        return 1 / (1 + Math.pow(10, (ratingB - ratingA) / 400.0));
    }

    /** Elo-Änderung für den Sieger (der Verlierer bekommt den negativen Wert). */
    public static int delta(int winnerRating, int loserRating) {
        return (int) Math.round(K * (1 - expected(winnerRating, loserRating)));
    }

    public static int levelOf(int xp) {
        return (int) Math.floor(Math.sqrt(Math.max(0, xp) / (double) XP_PER_LEVEL_STEP)) + 1;
    }

    static int xpForLevel(int level) {
        return XP_PER_LEVEL_STEP * (level - 1) * (level - 1);
    }

    /**
     * Liga-Punkte und XP nach einem gespeicherten Kampf verbuchen. Muss in derselben Transaktion wie das
     * Speichern laufen, die User sind dann verwaltet und werden mitgespeichert.
     */
    public void apply(BattleResult r) {
        User winner = r.getWinner();
        User loser = r.getLoser();
        boolean bot = r.getMode() == BattleMode.BOT;
        int xpFactor = bot ? 1 : 2;

        if (!bot) {
            int d = delta(rating(winner), rating(loser));
            winner.setRating(rating(winner) + d);
            loser.setRating(Math.max(START_RATING, rating(loser) - d));
            r.setRatingDeltaWinner(d);
            r.setRatingDeltaLoser(-d);
        } else {
            User human = isBot(winner) ? loser : winner;
            boolean humanWon = human == winner;
            int before = rating(human);
            int d = before >= BOT_RATING_CAP ? 0 : (int) Math.round(BOT_FACTOR * delta(before, before));
            human.setRating(Math.max(START_RATING, before + (humanWon ? d : -d)));
            if (humanWon) r.setRatingDeltaWinner(d);
            else r.setRatingDeltaLoser(-d);
        }

        if (!isBot(winner)) winner.setXp(xp(winner) + XP_WIN * xpFactor);
        if (!isBot(loser)) loser.setXp(xp(loser) + XP_LOSS * xpFactor);
    }

    public RankDto rankOf(User user) {
        return rankOf(rating(user), xp(user));
    }

    public static RankDto rankOf(int rating, int xp) {
        int tierIndex = 0;
        for (int i = 0; i < TIERS.size(); i++) {
            if (rating >= TIERS.get(i).floor()) tierIndex = i;
        }
        Tier tier = TIERS.get(tierIndex);
        boolean master = tierIndex == TIERS.size() - 1;

        Integer division = null;
        String league = tier.name();
        int divisionFloor = tier.floor();
        int progress = 100;
        String nextLeague = null;
        Integer pointsToNext = null;

        if (!master) {
            int step = Math.clamp((rating - tier.floor()) / DIVISION_WIDTH, 0, 2);
            division = 3 - step;
            divisionFloor = tier.floor() + step * DIVISION_WIDTH;
            league = tier.name() + " " + DIVISION_NAMES[division];
            progress = Math.clamp((int) Math.round(100.0 * (rating - divisionFloor) / DIVISION_WIDTH), 0, 100);
            int nextFloor = divisionFloor + DIVISION_WIDTH;
            Tier nextTier = division == 1 ? TIERS.get(tierIndex + 1) : tier;
            nextLeague = nextTier == TIERS.getLast()
                    ? nextTier.name()
                    : nextTier.name() + " " + DIVISION_NAMES[division == 1 ? 3 : division - 1];
            pointsToNext = Math.max(0, nextFloor - rating);
        }

        int level = levelOf(xp);
        return new RankDto(rating, tier.name(), division, league, divisionFloor, progress, nextLeague,
                pointsToNext, level, xp, xpForLevel(level), xpForLevel(level + 1));
    }

    public static int rating(User u) {
        return u.getRating() == null ? START_RATING : Math.max(START_RATING, u.getRating());
    }

    static int xp(User u) {
        return u.getXp() == null ? 0 : u.getXp();
    }

    static boolean isBot(User u) {
        return SystemUserSeeder.BOT_USERNAME.equals(u.getUsername());
    }
}
