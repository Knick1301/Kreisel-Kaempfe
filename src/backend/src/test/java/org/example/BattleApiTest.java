package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.entities.BattleResult;
import org.example.repositories.BattleResultRepository;
import org.example.services.RankingService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BattleApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired BattleResultRepository results;

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
                        .content("{\"name\":\"Testkreisel\",\"fusionWheelId\":" + ids[0]
                                + ",\"spinTrackId\":" + ids[1] + ",\"tipId\":" + ids[2] + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(created).get("id").asLong();
    }

    private JsonNode botBattle(String auth, long beybladeId) throws Exception {
        return json.readTree(mvc.perform(post("/api/battles/bot").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"beybladeId\":" + beybladeId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void botKampfLiefertKaempferUndTimeline() throws Exception {
        String auth = register("botfan1");
        JsonNode battle = botBattle(auth, buildBeyblade(auth));

        assertEquals("BOT", battle.get("mode").asText());
        assertTrue(battle.get("winner").asText().equals("KI") || battle.get("loser").asText().equals("KI"));
        assertTrue(battle.get("seed").asLong() >= 0 && battle.get("seed").asLong() < (1L << 53));
        int duration = battle.get("durationMs").asInt();
        assertTrue(duration >= 12_000 && duration <= 35_000);
        assertEquals("standard", battle.get("arena").get("id").asText());

        JsonNode me = battle.get("fighters").get(0);
        JsonNode bot = battle.get("fighters").get(1);
        assertEquals("botfan1", me.get("username").asText());
        assertFalse(me.get("bot").asBoolean());
        assertEquals(3, me.get("parts").size());
        assertTrue(bot.get("bot").asBoolean());
        assertEquals(2, bot.get("beyblade").asText().split(" ").length);
        assertTrue(bot.get("accent").asText().startsWith("#"));
        assertFalse(bot.has("parts"));
        assertTrue(me.has("archetype") && me.get("style").has("attack"));
        assertTrue(battle.get("matchup").has("edge"));

        JsonNode timeline = battle.get("timeline");
        assertEquals(100, timeline.get("sampleMs").asInt());
        assertEquals(duration / 100 + 1, timeline.get("energy").get("A").size());
        assertEquals("LAUNCH", timeline.get("events").get(0).get("type").asText());
        assertFalse(timeline.get("events").get(0).has("victim"), "null-Felder sollen fehlen");
    }

    @Test
    void replayErgibtIdentischeTimeline() throws Exception {
        String auth = register("botfan2");
        JsonNode battle = botBattle(auth, buildBeyblade(auth));
        JsonNode replay = json.readTree(mvc.perform(get("/api/battles/" + battle.get("id").asLong())
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(battle, replay);
    }

    @Test
    void ohneGleicheSimulationsversionGibtEsKeineTimeline() throws Exception {
        String auth = register("botfan3");
        long id = botBattle(auth, buildBeyblade(auth)).get("id").asLong();
        BattleResult stored = results.findById(id).orElseThrow();
        stored.setSimVersion(0);
        results.save(stored);

        mvc.perform(get("/api/battles/" + id).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finish").exists())
                .andExpect(jsonPath("$.fighters.length()").value(2))
                .andExpect(jsonPath("$.timeline").doesNotExist());
    }

    @Test
    void botKaempfeZaehlenReduziertFuerLigaUndRangliste() throws Exception {
        String auth = register("botfan4");
        long bey = buildBeyblade(auth);
        botBattle(auth, bey);
        botBattle(auth, bey);

        mvc.perform(get("/api/me/stats").header("Authorization", auth))
                .andExpect(jsonPath("$.wins").value(0))
                .andExpect(jsonPath("$.losses").value(0))
                .andExpect(jsonPath("$.rank").exists())
                .andExpect(result -> {
                    JsonNode s = json.readTree(result.getResponse().getContentAsString());
                    assertEquals(2, s.get("botWins").asLong() + s.get("botLosses").asLong());
                    int rating = s.get("ranking").get("rating").asInt();
                    long botWins = s.get("botWins").asLong();
                    assertTrue(rating >= RankingService.START_RATING
                                    && rating <= RankingService.START_RATING + 6 * botWins,
                            "KI-Kämpfe zählen ×0,4 und nie unter den Startwert");
                    assertTrue(s.get("ranking").get("xp").asInt() > 0);
                });

        JsonNode board = json.readTree(mvc.perform(get("/api/leaderboard?limit=100").header("Authorization", auth))
                .andReturn().getResponse().getContentAsString());
        for (JsonNode e : board.get("entries")) {
            assertFalse(e.get("username").asText().equals("KI"), "KI darf nicht in der Rangliste stehen");
        }

        mvc.perform(get("/api/me/battles").header("Authorization", auth))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].mode").value("BOT"))
                .andExpect(jsonPath("$[0].timeline").doesNotExist());
    }

    @Test
    void fehlerfaelleLiefernSaubereCodes() throws Exception {
        mvc.perform(post("/api/battles/bot").contentType(MediaType.APPLICATION_JSON).content("{\"beybladeId\":1}"))
                .andExpect(status().isUnauthorized())
                .andExpect(status().reason("Bitte melde dich an."));

        String auth = register("botfan5");
        mvc.perform(post("/api/battles/bot").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/battles/bot").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"beybladeId\":1}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/battles/bot").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"beybladeId\":999999}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/battles/999999").header("Authorization", auth))
                .andExpect(status().isNotFound());
    }

    @Test
    void arenaUndVarianteLassenSichWaehlenUndBleibenImReplay() throws Exception {
        String auth = register("botfan6");
        long bey = buildBeyblade(auth);
        JsonNode battle = json.readTree(mvc.perform(post("/api/battles/bot").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"beybladeId\":" + bey + ",\"arenaId\":\"zyklon\",\"variantId\":\"sand\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        assertEquals("zyklon", battle.get("arena").get("id").asText());
        assertEquals("Zyklon-Kessel", battle.get("arena").get("name").asText());
        assertEquals("sand", battle.get("arena").get("variant").asText());
        assertEquals(2, battle.get("arena").get("variants").size());

        JsonNode replay = json.readTree(mvc.perform(get("/api/battles/" + battle.get("id").asLong())
                        .header("Authorization", auth))
                .andReturn().getResponse().getContentAsString());
        assertEquals(battle.get("timeline"), replay.get("timeline"));
        assertEquals("sand", replay.get("arena").get("variant").asText());

        // ohne Variante: die Vorgabe der Arena
        JsonNode plain = json.readTree(mvc.perform(post("/api/battles/bot").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"beybladeId\":" + bey + ",\"arenaId\":\"gletscher\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        assertEquals("eis", plain.get("arena").get("variant").asText());

        mvc.perform(post("/api/battles/bot").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"beybladeId\":" + bey + ",\"arenaId\":\"mond\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("Unbekannte Arena \"mond\"."));
        mvc.perform(post("/api/battles/bot").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"beybladeId\":" + bey + ",\"arenaId\":\"oktagon\",\"variantId\":\"eis\"}"))
                .andExpect(status().isBadRequest());
        // ausgemusterte Arena ist nicht mehr wählbar
        mvc.perform(post("/api/battles/bot").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"beybladeId\":" + bey + ",\"arenaId\":\"krater\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void alteKraterKaempfeLassenSichWeiterNachspielen() throws Exception {
        String auth = register("botfan7");
        long bey = buildBeyblade(auth);
        // Kampf wie früher im Krater anlegen: Ergebnis aus der Simulation mit der alten Arena
        JsonNode battle = json.readTree(mvc.perform(post("/api/battles/bot").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"beybladeId\":" + bey + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        BattleResult r = results.findById(battle.get("id").asLong()).orElseThrow();
        var krater = org.example.game.Arena.find("krater").orElseThrow();
        var outcome = new org.example.game.Battle(krater, r.getFighterA().fighter(), r.getFighterB().fighter(),
                r.getSeed()).run();
        r.setArena("krater");
        r.setArenaVariant(null);
        r.setWinnerSide(outcome.winnerSide());
        r.setFinish(outcome.finish());
        r.setDurationMs(outcome.durationMs());
        results.save(r);

        JsonNode replay = json.readTree(mvc.perform(get("/api/battles/" + r.getId()).header("Authorization", auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("krater", replay.get("arena").get("id").asText());
        assertEquals("vulkan", replay.get("arena").get("variant").asText());
        assertTrue(replay.has("timeline"));
    }

    @Test
    void kiKannSichNichtAnmelden() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"KI\",\"password\":\"geheim123\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"KI\",\"password\":\"geheim123\"}"))
                .andExpect(status().isBadRequest());
    }
}
