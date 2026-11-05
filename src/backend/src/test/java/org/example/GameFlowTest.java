package org.example;

import org.example.dtos.BeybladeRequest;
import org.example.entities.BattleResult;
import org.example.entities.Beyblade;
import org.example.entities.User;
import org.example.game.Lobby;
import org.example.game.LobbyStatus;
import org.example.repositories.UserRepository;
import org.example.services.BattleService;
import org.example.services.BeybladeService;
import org.example.services.LeaderboardService;
import org.example.services.LobbyService;
import org.example.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class GameFlowTest {

    @Autowired UserService users;
    @Autowired UserRepository userRepository;
    @Autowired BeybladeService beyblades;
    @Autowired BattleService battles;
    @Autowired LeaderboardService leaderboard;
    @Autowired LobbyService lobbies;

    private static HttpStatus status(ResponseStatusException e) {
        return HttpStatus.valueOf(e.getStatusCode().value());
    }

    private Beyblade firstBeyblade(String username) {
        return beyblades.forUser(username).getFirst();
    }

    @Test
    void registrierungSpeichertGehashtesPasswortUndLoginKlappt() {
        User user = users.register("neuling", "geheim123");

        assertNotEquals("geheim123", user.getPassword());
        assertTrue(user.getPassword().startsWith("$2"));
        assertEquals("neuling", users.login("neuling", "geheim123").getUsername());

        ResponseStatusException wrong = assertThrows(ResponseStatusException.class,
                () -> users.login("neuling", "falsch123"));
        assertEquals(HttpStatus.UNAUTHORIZED, status(wrong));
    }

    @Test
    void registrierungPrueftEingaben() {
        assertEquals(HttpStatus.BAD_REQUEST, status(assertThrows(ResponseStatusException.class,
                () -> users.register("ab", "geheim123"))));
        assertEquals(HttpStatus.BAD_REQUEST, status(assertThrows(ResponseStatusException.class,
                () -> users.register("gueltig", "kurz1"))));
        assertEquals(HttpStatus.BAD_REQUEST, status(assertThrows(ResponseStatusException.class,
                () -> users.register("gueltig", "nurbuchstaben"))));
        assertEquals(HttpStatus.CONFLICT, status(assertThrows(ResponseStatusException.class,
                () -> users.register("demo", "geheim123"))));
    }

    @Test
    void demoPasswortIstGehasht() {
        assertTrue(userRepository.findByUsername("demo").orElseThrow().getPassword().startsWith("$2"));
        assertEquals("demo", users.login("demo", "demo").getUsername());
    }

    @Test
    void kreiselAnlegenAendernUndLoeschen() {
        Beyblade existing = firstBeyblade("demo");
        Long wheel = existing.getFusionWheel().getId();
        Long track = existing.getSpinTrack().getId();
        Long tip = existing.getTip().getId();

        Beyblade created = beyblades.create("demo", new BeybladeRequest("  Neuer  ", wheel, track, tip));
        assertEquals("Neuer", created.getName());

        Beyblade renamed = beyblades.update("demo", created.getId(), new BeybladeRequest("Umbenannt", wheel, track, tip));
        assertEquals("Umbenannt", renamed.getName());

        ResponseStatusException duplicate = assertThrows(ResponseStatusException.class,
                () -> beyblades.update("demo", created.getId(),
                        new BeybladeRequest(existing.getName(), wheel, track, tip)));
        assertEquals(HttpStatus.CONFLICT, status(duplicate));

        beyblades.delete("demo", created.getId());
        assertTrue(beyblades.forUser("demo").stream().noneMatch(b -> b.getId().equals(created.getId())));
    }

    @Test
    void fremdeKreiselKannManNichtAendern() {
        Beyblade theirs = firstBeyblade("rivale");
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> beyblades.delete("demo", theirs.getId()));
        assertEquals(HttpStatus.NOT_FOUND, status(e));
    }

    @Test
    void schnellesMatchSpeichertErgebnisUndZaehltInDerRangliste() {
        BattleResult result = battles.quickMatch("demo", firstBeyblade("demo").getId());

        assertNotNull(result.getId());
        assertTrue(result.getWinner().getUsername().equals("demo") || result.getLoser().getUsername().equals("demo"));
        assertEquals(1, leaderboard.stats("demo").wins() + leaderboard.stats("demo").losses());
        assertNotNull(leaderboard.leaderboard("demo", 10).me());
        assertEquals(1, battles.history("demo", 10).size());
    }

    @Test
    void lobbyErstellenBeitretenUndKampfStarten() {
        Lobby lobby = lobbies.create("Testlobby", "demo", firstBeyblade("demo").getId());
        assertEquals(LobbyStatus.OPEN, lobby.getStatus());
        assertTrue(lobbies.open().contains(lobby));

        lobbies.join(lobby.getId().toLowerCase(), "rivale", firstBeyblade("rivale").getId());
        assertEquals(LobbyStatus.READY, lobby.getStatus());
        assertTrue(!lobbies.open().contains(lobby));

        ResponseStatusException notHost = assertThrows(ResponseStatusException.class,
                () -> lobbies.start(lobby.getId(), "rivale"));
        assertEquals(HttpStatus.FORBIDDEN, status(notHost));

        BattleResult result = lobbies.start(lobby.getId(), "demo");
        assertEquals(LobbyStatus.FINISHED, lobby.getStatus());
        assertEquals(result.getId(), lobby.getBattleResultId());
    }

    @Test
    void lobbyRegeln() {
        Long demoBey = firstBeyblade("wirbelwind").getId();
        Lobby lobby = lobbies.create("Regeln", "wirbelwind", demoBey);

        assertEquals(HttpStatus.BAD_REQUEST, status(assertThrows(ResponseStatusException.class,
                () -> lobbies.join(lobby.getId(), "wirbelwind", demoBey))));
        assertEquals(HttpStatus.CONFLICT, status(assertThrows(ResponseStatusException.class,
                () -> lobbies.start(lobby.getId(), "wirbelwind"))));
        assertEquals(HttpStatus.CONFLICT, status(assertThrows(ResponseStatusException.class,
                () -> lobbies.create("Zweite", "wirbelwind", demoBey))));

        lobbies.leave(lobby.getId(), "wirbelwind");
        assertEquals(HttpStatus.NOT_FOUND, status(assertThrows(ResponseStatusException.class,
                () -> lobbies.get(lobby.getId()))));
    }
}
