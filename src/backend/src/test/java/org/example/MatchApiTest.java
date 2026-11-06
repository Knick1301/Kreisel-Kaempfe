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
        assertEquals(3, c.get("maxTaps").asInt());
        assertEquals(3_000, c.get("windowMs").asInt());
        assertTrue(c.get("periodMs").asInt() > 0);
    }

    @Test
    void anspinnenLiefertKampfMitAusgewertetenStarts() throws Exception {
        String auth = register("spinner2");
        JsonNode m = create(auth, "{\"beybladeId\":" + buildBeyblade(auth) + "}");
        JsonNode c = m.get("launch");
        int period = c.get("periodMs").asInt();
        // Zeitpunkte, zu denen der Zeiger genau auf den Zonenmitten steht (Hinweg, dann Rückweg)
        StringBuilder taps = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            double z = c.get("zones").get(i).asDouble();
            int t = (int) Math.round(period / (2 * Math.PI) * Math.acos(1 - 2 * z));
            if (i > 0) taps.append(',');
            taps.append(t);
        }

        JsonNode done = launch(auth, m.get("id").asText(), "{\"taps\":[" + taps + "]}");

        assertEquals("DONE", done.get("status").asText());
        JsonNode report = done.get("report");
        assertEquals(done.get("battleId").asLong(), report.get("id").asLong());
        assertEquals("BOT", report.get("mode").asText());
        assertTrue(report.has("timeline"), "Kampf mit Anspinnen muss nachspielbar sein");

        JsonNode mine = report.get("fighters").get(0).get("launch");
        assertEquals(3, mine.get("hits").asInt());
        assertEquals(3, mine.get("cores").asInt());
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
        JsonNode done = launch(auth, m.get("id").asText(), "{}");
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

        launch(auth, id, "{\"taps\":[400,900,1500]}");
        mvc.perform(post("/api/matches/" + id + "/launch").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isConflict());
        mvc.perform(get("/api/matches/unbekannt").header("Authorization", auth)).andExpect(status().isNotFound());
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
