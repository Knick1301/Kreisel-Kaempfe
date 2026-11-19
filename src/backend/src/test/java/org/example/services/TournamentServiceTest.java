package org.example.services;

import org.example.dtos.BeybladeStats;
import org.example.dtos.TournamentDto;
import org.example.entities.User;
import org.example.game.Arena;
import org.example.game.Battle;
import org.example.game.BattleTuning;
import org.example.game.BotFactory;
import org.example.game.BotLauncher;
import org.example.game.Fighter;
import org.example.game.LaunchChallenge;
import org.example.game.LaunchInput;
import org.example.game.Side;
import org.example.game.UltInput;
import org.example.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Solo-Cup: Baum, Stufen, Fortschritt, keine Liga-Punkte. */
@SpringBootTest
@Transactional
class TournamentServiceTest {

    @Autowired TournamentService cups;
    @Autowired MatchService matches;
    @Autowired ArenaRoomService rooms;
    @Autowired UserRepository users;

    private Long demoBey() {
        return users.findByUsername("demo").orElseThrow().getBeybladeConfigurations().getFirst().getId();
    }

    @Test
    void rundeDerBegegnungImBaum() {
        assertEquals(0, TournamentService.meetRound(0, 1));
        assertEquals(1, TournamentService.meetRound(0, 2));
        assertEquals(1, TournamentService.meetRound(1, 3));
        assertEquals(2, TournamentService.meetRound(0, 4));
        assertEquals(2, TournamentService.meetRound(3, 7));
    }

    @Test
    void cupStartetMitAchtTeilnehmernUndViertelfinale() {
        TournamentDto t = cups.start("demo", demoBey(), "CLASSIC");
        assertEquals("RUNNING", t.status());
        assertEquals("Viertelfinale", t.roundName());
        assertEquals(4, t.upcoming().size());
        long you = t.upcoming().stream().filter(p -> p.a().you() || p.b().you()).count();
        assertEquals(1, you);
        // eigener Viertelfinal-Gegner hat Stufe 0
        var mine = t.upcoming().stream().filter(p -> p.a().you() || p.b().you()).findFirst().orElseThrow();
        var opp = mine.a().you() ? mine.b() : mine.a();
        assertEquals(0, opp.level());
        assertNotNull(t.opponent());
        assertNull(t.matchId());
    }

    @Test
    void kampfWirdGewertetOhneLigaPunkteUndDerBaumRueckt() {
        User before = users.findByUsername("demo").orElseThrow();
        Integer rating = before.getRating();
        int xp = before.getXp() == null ? 0 : before.getXp();

        cups.start("demo", demoBey(), "CLASSIC");
        TournamentDto t = cups.next("demo");
        String matchId = t.matchId();
        assertNotNull(matchId);
        // erneutes „Nächster Kampf“ liefert dasselbe Match
        assertEquals(matchId, cups.next("demo").matchId());

        matches.launch(matchId, "demo", LaunchInput.NONE);
        matches.finish(matchId, "demo");
        t = cups.current("demo");

        assertEquals(1, t.results().size());
        assertEquals(4, t.results().getFirst().size());
        assertTrue(t.results().getFirst().stream().allMatch(p -> p.winner() != null && p.finish() != null));
        assertNull(t.matchId());
        User after = users.findByUsername("demo").orElseThrow();
        assertEquals(rating, after.getRating());
        assertTrue(after.getXp() > xp);
        if (t.status().equals("RUNNING")) {
            assertEquals("Halbfinale", t.roundName());
            assertEquals(2, t.upcoming().size());
        } else {
            assertEquals("OUT", t.status());
            assertThrows(ResponseStatusException.class, () -> cups.next("demo"));
        }
    }

    @Test
    void arenaCupLegtEinenArenaRaumOhneLigaPunkteAn() {
        TournamentDto t = cups.start("demo", demoBey(), "ARENA");
        assertEquals("ARENA", t.format());
        t = cups.next("demo");
        var room = rooms.get(t.matchId(), "demo");
        assertTrue(room.cup());
        assertEquals("BOT", room.mode());
        assertEquals(t.opponent().beyblade(), room.fighters().get(1).beyblade());
    }

    /** Die Stufen machen die KI messbar stärker: gegen die Final-Stufe gewinnt man seltener. */
    @Test
    void spaetereRundenSindSchwerer() {
        BeybladeStats player = new BeybladeStats(6, 6, 8, 5, 78, 3);
        Random rng = new Random(7);
        int[] wins = new int[3];
        int n = 600;
        for (int level = 0; level < 3; level++) {
            for (int i = 0; i < n; i++) {
                var bot = BotFactory.create(player, rng, TournamentService.STRENGTH[level]);
                LaunchChallenge c = LaunchChallenge.random(rng);
                var lp = c.score(BotLauncher.input(c, rng, 0.5));
                var lb = c.score(BotLauncher.input(c, rng, TournamentService.SKILL[level]));
                var o = new Battle(Arena.standard(), new Fighter("p", "p", player), new Fighter("b", "b", bot.stats()),
                        rng.nextLong(), lp, lb, BattleTuning.SIM_VERSION, UltInput.AUTO, UltInput.AUTO).run();
                if (o.winnerSide() == Side.A) wins[level]++;
            }
        }
        assertTrue(wins[0] > wins[1] && wins[1] > wins[2],
                "Siege gegen Stufe 0/1/2: " + wins[0] + "/" + wins[1] + "/" + wins[2]);
    }
}
