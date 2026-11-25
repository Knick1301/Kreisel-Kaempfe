package org.example.services;

import org.example.dtos.BeybladeRequest;
import org.example.dtos.FriendsDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Freunde: Anfragen, Annehmen, Entfernen und Herausforderungen. */
@SpringBootTest
@Transactional
class FriendServiceTest {

    @Autowired FriendService friends;
    @Autowired UserService users;
    @Autowired BeybladeService beyblades;
    @Autowired PresenceService presence;
    @Autowired MatchService matches;

    private Long player(String name) {
        users.register(name, "geheim123");
        return beyblades.create(name, new BeybladeRequest("Bey " + name, 1L, 1L, 1L)).getId();
    }

    @Test
    void anfrageAnnehmenUndEntfernen() {
        player("freundA");
        player("freundB");

        FriendsDto a = friends.request("freundA", "FREUNDB");
        assertEquals(1, a.outgoing().size());
        FriendsDto b = friends.list("freundB");
        assertEquals(1, b.incoming().size());
        assertEquals("freundA", b.incoming().getFirst().username());

        // B kann nicht doppelt, A nicht nochmal anfragen
        assertThrows(ResponseStatusException.class, () -> friends.request("freundA", "freundB"));

        b = friends.accept("freundB", b.incoming().getFirst().id());
        assertEquals(1, b.friends().size());
        assertNotNull(b.friends().getFirst().ranking());
        assertEquals(1, friends.list("freundA").friends().size());

        friends.remove("freundA", b.friends().getFirst().id());
        assertTrue(friends.list("freundB").friends().isEmpty());
    }

    @Test
    void gegenseitigeAnfrageIstSofortFreundschaft() {
        player("gegenA");
        player("gegenB");
        friends.request("gegenA", "gegenB");
        FriendsDto b = friends.request("gegenB", "gegenA");
        assertEquals(1, b.friends().size());
        assertTrue(b.incoming().isEmpty());
    }

    @Test
    void ungueltigeAnfragen() {
        player("soloA");
        ResponseStatusException self = assertThrows(ResponseStatusException.class,
                () -> friends.request("soloA", "SoloA"));
        assertEquals(HttpStatus.BAD_REQUEST, self.getStatusCode());
        ResponseStatusException unknown = assertThrows(ResponseStatusException.class,
                () -> friends.request("soloA", "gibtsnicht"));
        assertEquals(HttpStatus.NOT_FOUND, unknown.getStatusCode());
    }

    @Test
    void onlineStatus() {
        player("onA");
        player("onB");
        friends.request("onA", "onB");
        friends.request("onB", "onA");
        presence.seen("onB");
        assertTrue(friends.list("onA").friends().getFirst().online());
        assertFalse(friends.list("onB").friends().getFirst().online());
    }

    @Test
    void herausforderungStartetPvpMatchFuerBeide() {
        Long beyA = player("duellA");
        Long beyB = player("duellB");

        // nur unter Freunden
        assertThrows(ResponseStatusException.class, () -> friends.challenge("duellA", "duellB", beyA, "CLASSIC"));
        friends.request("duellA", "duellB");
        friends.request("duellB", "duellA");

        FriendsDto a = friends.challenge("duellA", "duellB", beyA, "CLASSIC");
        assertEquals(1, a.challenges().size());
        assertTrue(a.challenges().getFirst().mine());
        // keine zweite offene Herausforderung
        assertThrows(ResponseStatusException.class, () -> friends.challenge("duellB", "duellA", beyB, null));

        FriendsDto b = friends.list("duellB");
        var challenge = b.challenges().getFirst();
        assertFalse(challenge.mine());
        b = friends.acceptChallenge("duellB", challenge.id(), beyB);
        String matchId = b.challenges().getFirst().matchId();
        assertNotNull(matchId);
        assertEquals(matchId, friends.list("duellA").challenges().getFirst().matchId());
        assertEquals("PVP", matches.get(matchId, "duellA").mode().name());
    }
}
