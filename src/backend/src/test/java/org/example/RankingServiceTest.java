package org.example;

import org.example.config.SystemUserSeeder;
import org.example.dtos.RankDto;
import org.example.entities.BattleResult;
import org.example.entities.User;
import org.example.game.BattleMode;
import org.example.services.RankingService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankingServiceTest {

    private final RankingService ranking = new RankingService();

    private static User user(String name, Integer rating) {
        User u = new User();
        u.setUsername(name);
        u.setRating(rating);
        return u;
    }

    private static BattleResult result(BattleMode mode, User winner, User loser) {
        BattleResult r = new BattleResult();
        r.setMode(mode);
        r.setWinner(winner);
        r.setLoser(loser);
        return r;
    }

    @Test
    void eloIstSymmetrischUndBelohntUeberraschungen() {
        assertEquals(16, RankingService.delta(1000, 1000));
        assertTrue(RankingService.delta(1000, 1400) > 25, "Sieg gegen Stärkeren bringt viel");
        assertTrue(RankingService.delta(1400, 1000) < 7, "Sieg gegen Schwächeren bringt wenig");
        assertEquals(1.0, RankingService.expected(1200, 1000) + RankingService.expected(1000, 1200), 1e-9);
    }

    @Test
    void pvpVerschiebtPunkteZwischenBeiden() {
        User a = user("anna", 1000);
        User b = user("bert", 1000);
        BattleResult r = result(BattleMode.PVP, a, b);

        ranking.apply(r);

        assertEquals(1016, a.getRating());
        assertEquals(984, b.getRating());
        assertEquals(16, r.getRatingDeltaWinner());
        assertEquals(-16, r.getRatingDeltaLoser());
        assertEquals(100, a.getXp());
        assertEquals(40, b.getXp());
    }

    @Test
    void ersterKampfStartetBeimStartwertUndNiemandFaelltDarunter() {
        User a = user("anna", null);
        User b = user("bert", null);
        ranking.apply(result(BattleMode.PVP, a, b));
        assertEquals(RankingService.START_RATING + 16, a.getRating());
        assertEquals(RankingService.START_RATING, b.getRating(), "Bronze III ist die unterste Stufe");

        User bot = user(SystemUserSeeder.BOT_USERNAME, null);
        User c = user("carl", RankingService.START_RATING + 2);
        ranking.apply(result(BattleMode.BOT, bot, c));
        assertEquals(RankingService.START_RATING, c.getRating());
    }

    @Test
    void kiKaempfeZaehlenReduziertUndNurBisGold() {
        User bot = user(SystemUserSeeder.BOT_USERNAME, null);
        User low = user("anna", 1000);
        BattleResult win = result(BattleMode.BOT, low, bot);
        ranking.apply(win);
        assertEquals(1006, low.getRating());
        assertEquals(6, win.getRatingDeltaWinner());
        assertNull(win.getRatingDeltaLoser());
        assertNull(bot.getRating(), "Die KI bekommt nie Punkte");
        assertNull(bot.getXp());
        assertEquals(50, low.getXp());

        User loser = user("bert", 1000);
        ranking.apply(result(BattleMode.BOT, bot, loser));
        assertEquals(994, loser.getRating());

        User gold = user("carl", RankingService.BOT_RATING_CAP);
        BattleResult capped = result(BattleMode.BOT, gold, bot);
        ranking.apply(capped);
        assertEquals(RankingService.BOT_RATING_CAP, gold.getRating());
        assertEquals(0, capped.getRatingDeltaWinner());
        assertEquals(50, gold.getXp(), "XP gibt es trotzdem");
    }

    @Test
    void ligenUndDivisionen() {
        assertEquals("Bronze III", RankingService.rankOf(RankingService.START_RATING, 0).league());
        assertEquals("Bronze III", RankingService.rankOf(RankingService.START_RATING + 49, 0).league());
        assertEquals("Bronze II", RankingService.rankOf(1000, 0).league());
        assertEquals("Bronze I", RankingService.rankOf(1099, 0).league());
        assertEquals("Silber III", RankingService.rankOf(1100, 0).league());
        assertEquals("Gold I", RankingService.rankOf(1399, 0).league());
        assertEquals("Diamant I", RankingService.rankOf(1650, 0).league());

        RankDto master = RankingService.rankOf(2000, 0);
        assertEquals("Meister", master.league());
        assertNull(master.division());
        assertNull(master.nextLeague());

        RankDto silver = RankingService.rankOf(1125, 0);
        assertEquals("Silber III", silver.league());
        assertEquals("Silber II", silver.nextLeague());
        assertEquals(25, silver.pointsToNext());
        assertEquals(50, silver.progress());

        assertEquals("Silber III", RankingService.rankOf(1060, 0).nextLeague());
        assertEquals("Meister", RankingService.rankOf(1690, 0).nextLeague());
    }

    @Test
    void levelAusXp() {
        assertEquals(1, RankingService.levelOf(0));
        assertEquals(1, RankingService.levelOf(49));
        assertEquals(2, RankingService.levelOf(50));
        assertEquals(3, RankingService.levelOf(200));
        RankDto r = RankingService.rankOf(1000, 120);
        assertEquals(2, r.level());
        assertEquals(50, r.xpLevelStart());
        assertEquals(200, r.xpNextLevel());
    }
}
