package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Match-Ablauf mit Anspinnen gegen die KI. */
@SpringBootTest
@AutoConfigureMockMvc
class MatchApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String register(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"geheim123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("token").asText();
    }

    private long buildBeyblade(String auth) throws Exception {
        long[] ids = new long[3];
        String[] kinds = {"fusion-wheels", "spin-tracks", "tips"};
        for (int i = 0; i < 3; i++) {
            ids[i] = json.readTree(mvc.perform(get("/api/parts/" + kinds[i]).header("Authorization", auth))
                    .andReturn().getResponse().getContentAsString()).get(0).get("id").asLong();
        }
        String created = mvc.perform(post("/api/me/beyblades").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Startkreisel\",\"fusionWheelId\":" + ids[0]
                                + ",\"spinTrackId\":" + ids[1] + ",\"tipId\":" + ids[2] + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(created).get("id").asLong();
    }

    private JsonNode create(String auth, String body) throws Exception {
        return json.readTree(mvc.perform(post("/api/matches").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode launch(String auth, String matchId, String body) throws Exception {
        return json.readTree(mvc.perform(post("/api/matches/" + matchId + "/launch").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode finish(String auth, String matchId) throws Exception {
        return json.readTree(mvc.perform(post("/api/matches/" + matchId + "/finish").header("Authorization", auth))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private org.springframework.test.web.servlet.ResultActions ult(String auth, String matchId, int t)
            throws Exception {
        return mvc.perform(post("/api/matches/" + matchId + "/ult").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"t\":" + t + "}"));
    }

    /** Zeitpunkt, ab dem die eigene Ult bereit ist (ULT_READY von A), oder -1. */
    private static int readyAt(JsonNode report) {
        for (JsonNode e : report.get("timeline").get("events")) {
            if ("ULT_READY".equals(e.get("type").asText()) && "A".equals(e.get("side").asText())) {
                return e.get("t").asInt();
            }
        }
        return -1;
    }

    @Test
    void matchZeigtGegnerUndVorgabenAberKeinenFremdenStart() throws Exception {
        String auth = register("spinner1");
        JsonNode m = create(auth, "{\"beybladeId\":" + buildBeyblade(auth)
                + ",\"arenaId\":\"oktagon\",\"variantId\":\"neon\"}");

        assertEquals("LAUNCH", m.get("status").asText());
        assertEquals("A", m.get("yourSide").asText());
        assertEquals("oktagon", m.get("arena").get("id").asText());
        assertEquals("neon", m.get("arena").get("variant").asText());
        assertEquals(2, m.get("fighters").size());
        assertTrue(m.get("fighters").get(1).get("bot").asBoolean());
        assertFalse(m.get("fighters").get(1).has("launch"), "Start der KI darf vorher nicht sichtbar sein");
        assertFalse(m.get("youLaunched").asBoolean());
        assertTrue(m.get("opponentReady").asBoolean());
        assertTrue(m.get("expiresInMs").asLong() > 0);
        assertFalse(m.has("report"));

        JsonNode c = m.get("launch");
        assertEquals(3, c.get("zones").size());
        assertEquals(1, c.get("maxTaps").asInt());
        assertEquals(3_000, c.get("windowMs").asInt());
        assertTrue(c.get("periodMs").asInt() > 0);
    }

    @Test
    void anspinnenLiefertKampfMitAusgewertetenStarts() throws Exception {
        String auth = register("spinner2");
        JsonNode m = create(auth, "{\"beybladeId\":" + buildBeyblade(auth) + "}");
        JsonNode c = m.get("launch");
        int period = c.get("periodMs").asInt();
        // Loslassen, wenn der Zeiger genau auf der Mitte der ersten Zone steht, voll und schnell gezogen
        double z = c.get("zones").get(0).asDouble();
        int t = (int) Math.round(period / (2 * Math.PI) * Math.acos(1 - 2 * z));

        JsonNode fight = launch(auth, m.get("id").asText(),
                "{\"taps\":[" + t + "],\"pull\":1,\"speed\":1}");
        // Kampf läuft: vorläufig, noch nicht gespeichert
        assertEquals("FIGHT", fight.get("status").asText());
        assertFalse(fight.has("battleId"));
        assertTrue(fight.get("report").has("timeline"));

        JsonNode done = finish(auth, m.get("id").asText());
        assertEquals("DONE", done.get("status").asText());
        assertEquals(fight.get("report").get("durationMs").asInt(), done.get("report").get("durationMs").asInt());
        JsonNode report = done.get("report");
        assertEquals(done.get("battleId").asLong(), report.get("id").asLong());
        assertEquals("BOT", report.get("mode").asText());
        assertTrue(report.has("timeline"), "Kampf mit Anspinnen muss nachspielbar sein");

        JsonNode mine = report.get("fighters").get(0).get("launch");
        assertEquals(1, mine.get("hits").asInt());
        assertEquals(1, mine.get("cores").asInt());
        assertTrue(mine.get("perfect").asBoolean());
        assertTrue(report.get("fighters").get(1).has("launch"));

        // Replay über den normalen Endpunkt kennt die Starts ebenfalls
        JsonNode replay = json.readTree(mvc.perform(get("/api/battles/" + done.get("battleId").asLong())
                        .header("Authorization", auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(replay.has("timeline"));
        assertEquals(report.get("durationMs").asInt(), replay.get("durationMs").asInt());
        assertEquals(mine.get("quality").asDouble(),
                replay.get("fighters").get(0).get("launch").get("quality").asDouble());
    }

    @Test
    void verpassterStartZaehltTrotzdem() throws Exception {
        String auth = register("spinner3");
        JsonNode m = create(auth, "{\"beybladeId\":" + buildBeyblade(auth) + "}");
        launch(auth, m.get("id").asText(), "{}");
        JsonNode done = finish(auth, m.get("id").asText());
        JsonNode mine = done.get("report").get("fighters").get(0).get("launch");
        assertTrue(mine.get("missed").asBoolean());
        assertEquals(0.0, mine.get("quality").asDouble());
        assertEquals(0, mine.get("hits").asInt());
    }

    @Test
    void doppeltAnspinnenUndFremdeMatchesSindGesperrt() throws Exception {
        String auth = register("spinner4");
        String other = register("spinner5");
        JsonNode m = create(auth, "{\"beybladeId\":" + buildBeyblade(auth) + "}");
        String id = m.get("id").asText();

        mvc.perform(get("/api/matches/" + id).header("Authorization", other)).andExpect(status().isNotFound());
        mvc.perform(post("/api/matches/" + id + "/launch").header("Authorization", other)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());

        launch(auth, id, "{\"taps\":[900],\"pull\":0.8,\"speed\":0.7}");
        mvc.perform(post("/api/matches/" + id + "/launch").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isConflict());
        mvc.perform(get("/api/matches/unbekannt").header("Authorization", auth)).andExpect(status().isNotFound());
    }

    @Test
    void ultLoestErstAufDruckAusUndBleibtImReplay() throws Exception {
        String auth = register("spinner8");
        long bey = buildBeyblade(auth);
        // Bis ein Kampf lange genug läuft, dass die eigene Ult voll wird
        for (int attempt = 0; attempt < 20; attempt++) {
            JsonNode m = create(auth, "{\"beybladeId\":" + bey + "}");
            String id = m.get("id").asText();
            JsonNode fight = launch(auth, id, "{\"taps\":[900],\"pull\":0.8,\"speed\":0.7}");
            JsonNode report = fight.get("report");
            int ready = readyAt(report);
            int duration = report.get("durationMs").asInt();
            if (ready < 0 || ready + 600 >= duration) {
                finish(auth, id);
                continue;
            }
            // ohne Druck löst die eigene Ult nie aus
            for (JsonNode e : report.get("timeline").get("events")) {
                assertFalse("ULT".equals(e.get("type").asText()) && "A".equals(e.get("side").asText()));
            }
            // vor dem Bereitsein: abgelehnt
            ult(auth, id, ready - 500).andExpect(status().isConflict());

            int press = ready + 300;
            JsonNode after = json.readTree(ult(auth, id, press).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            boolean fired = false;
            for (JsonNode e : after.get("report").get("timeline").get("events")) {
                if ("ULT".equals(e.get("type").asText()) && "A".equals(e.get("side").asText())) {
                    fired = true;
                    assertTrue(e.get("t").asInt() >= press && e.get("t").asInt() < press + 50);
                    break;
                }
            }
            assertTrue(fired, "Ult muss beim Druck auslösen");
            // derselbe Zeitpunkt nochmal: abgelehnt
            ult(auth, id, press).andExpect(status().isConflict());

            JsonNode done = finish(auth, id);
            assertEquals("DONE", done.get("status").asText());
            JsonNode replay = json.readTree(mvc.perform(get("/api/battles/" + done.get("battleId").asLong())
                            .header("Authorization", auth))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertTrue(replay.has("timeline"), "Replay muss den Druck kennen");
            assertEquals(after.get("report").get("durationMs").asInt(), replay.get("durationMs").asInt());
            // nach dem Werten keine Ult mehr
            ult(auth, id, press + 1000).andExpect(status().isConflict());
            return;
        }
        throw new AssertionError("Kein Kampf, in dem die Ult rechtzeitig voll wurde");
    }

    @Test
    void neuesMatchWertetDenLaufendenKampf() throws Exception {
        String auth = register("spinner9");
        long bey = buildBeyblade(auth);
        JsonNode m = create(auth, "{\"beybladeId\":" + bey + "}");
        launch(auth, m.get("id").asText(), "{}");
        int before = json.readTree(mvc.perform(get("/api/me/battles").header("Authorization", auth))
                .andReturn().getResponse().getContentAsString()).size();
        create(auth, "{\"beybladeId\":" + bey + "}");
        int after = json.readTree(mvc.perform(get("/api/me/battles").header("Authorization", auth))
                .andReturn().getResponse().getContentAsString()).size();
        assertEquals(before + 1, after, "Abbrechen per Neustart darf keine Niederlage verhindern");
    }

    private JsonNode getMatch(String auth, String id) throws Exception {
        return json.readTree(mvc.perform(get("/api/matches/" + id).header("Authorization", auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    /** Zwei Spieler, Lobby, Start: Match-ID für beide. */
    private String pvpViaLobby(String host, long hostBey, String guest, long guestBey) throws Exception {
        String lobbyId = json.readTree(mvc.perform(post("/api/lobbies").header("Authorization", host)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Duell\",\"beybladeId\":" + hostBey + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        mvc.perform(post("/api/lobbies/" + lobbyId + "/join").header("Authorization", guest)
                .contentType(MediaType.APPLICATION_JSON).content("{\"beybladeId\":" + guestBey + "}"))
                .andExpect(status().isOk());
        JsonNode started = json.readTree(mvc.perform(post("/api/lobbies/" + lobbyId + "/start")
                        .header("Authorization", host))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        // der Gast sieht die Match-ID beim nächsten Abfragen der Lobby
        JsonNode seen = json.readTree(mvc.perform(get("/api/lobbies/" + lobbyId).header("Authorization", guest))
                .andReturn().getResponse().getContentAsString());
        assertEquals(started.get("matchId").asText(), seen.get("matchId").asText());
        return started.get("matchId").asText();
    }

    @Test
    void pvpUeberLobbyBeideSpinnenAnUltUndWertungErstWennBeideFertig() throws Exception {
        String host = register("duell1");
        String guest = register("duell2");
        long hostBey = buildBeyblade(host);
        long guestBey = buildBeyblade(guest);

        for (int attempt = 0; attempt < 20; attempt++) {
            String id = pvpViaLobby(host, hostBey, guest, guestBey);
            JsonNode forGuest = getMatch(guest, id);
            assertEquals("PVP", forGuest.get("mode").asText());
            assertEquals("B", forGuest.get("yourSide").asText());
            assertFalse(forGuest.get("fighters").get(1).get("bot").asBoolean());

            // Der Host spinnt an: noch kein Kampf, weil der Gast fehlt
            JsonNode afterHost = launch(host, id, "{\"taps\":[700],\"pull\":0.8,\"speed\":0.7}");
            assertEquals("LAUNCH", afterHost.get("status").asText());
            assertFalse(afterHost.get("opponentReady").asBoolean());
            JsonNode afterGuest = launch(guest, id, "{\"taps\":[700],\"pull\":0.8,\"speed\":0.7}");
            assertEquals("FIGHT", afterGuest.get("status").asText());
            int version = afterGuest.get("version").asInt();

            // Ult des Gasts (Seite B) suchen
            int ready = -1;
            for (JsonNode e : afterGuest.get("report").get("timeline").get("events")) {
                if ("ULT_READY".equals(e.get("type").asText()) && "B".equals(e.get("side").asText())) {
                    ready = e.get("t").asInt();
                    break;
                }
            }
            int duration = afterGuest.get("report").get("durationMs").asInt();
            if (ready < 0 || ready + 600 >= duration) {
                finish(host, id);
                finish(guest, id);
                continue;
            }
            ult(guest, id, ready + 200).andExpect(status().isOk());

            // Der Host erkennt die Änderung an der Version und sieht die Ult von B im Verlauf
            JsonNode hostView = getMatch(host, id);
            assertEquals(version + 1, hostView.get("version").asInt());
            boolean seen = false;
            for (JsonNode e : hostView.get("report").get("timeline").get("events")) {
                if ("ULT".equals(e.get("type").asText()) && "B".equals(e.get("side").asText())) seen = true;
            }
            assertTrue(seen, "Host muss die Ult des Gasts sehen");

            // Erst wenn beide fertig sind, wird gewertet
            assertEquals("FIGHT", finish(host, id).get("status").asText());
            JsonNode done = finish(guest, id);
            assertEquals("DONE", done.get("status").asText());
            assertEquals("PVP", done.get("report").get("mode").asText());
            assertTrue(done.get("report").has("ratingDeltaWinner"));
            return;
        }
        throw new AssertionError("Kein Kampf, in dem die Ult des Gasts rechtzeitig voll wurde");
    }

    @Test
    void spielersuchePaartZweiSpieler() throws Exception {
        String a = register("sucher1");
        String b = register("sucher2");
        long beyA = buildBeyblade(a);
        long beyB = buildBeyblade(b);

        JsonNode first = json.readTree(mvc.perform(post("/api/matchmaking").header("Authorization", a)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"beybladeId\":" + beyA + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("WAITING", first.get("state").asText());

        JsonNode second = json.readTree(mvc.perform(post("/api/matchmaking").header("Authorization", b)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"beybladeId\":" + beyB + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("FOUND", second.get("state").asText());

        JsonNode firstNow = json.readTree(mvc.perform(get("/api/matchmaking").header("Authorization", a))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("FOUND", firstNow.get("state").asText());
        assertEquals(second.get("matchId").asText(), firstNow.get("matchId").asText());
        assertEquals("PVP", getMatch(a, firstNow.get("matchId").asText()).get("mode").asText());
    }

    @Test
    void unbekannteArenaUndFremderKreiselWerdenAbgelehnt() throws Exception {
        String auth = register("spinner6");
        String other = register("spinner7");
        long bey = buildBeyblade(auth);
        mvc.perform(post("/api/matches").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"beybladeId\":" + bey + ",\"arenaId\":\"mond\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/matches").header("Authorization", other).contentType(MediaType.APPLICATION_JSON)
                .content("{\"beybladeId\":" + bey + "}")).andExpect(status().is4xxClientError());
    }
}
