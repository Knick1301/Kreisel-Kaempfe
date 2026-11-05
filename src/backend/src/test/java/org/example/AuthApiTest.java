package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.repositories.AuthTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AuthTokenRepository tokens;

    private String register(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"geheim123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    @Test
    void ohneAnmeldungIstAllesGesperrt() throws Exception {
        mvc.perform(get("/api/me/beyblades")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/leaderboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/parts/tips")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/beyblades").header("Authorization", "Bearer quatsch"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrierenBautKreiselUndKaempft() throws Exception {
        String token = register("spieler1");
        String auth = "Bearer " + token;

        mvc.perform(get("/api/auth/me").header("Authorization", auth))
                .andExpect(jsonPath("$.username").value("spieler1"));
        mvc.perform(get("/api/me/beyblades").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        JsonNode wheel = json.readTree(mvc.perform(get("/api/parts/fusion-wheels").header("Authorization", auth))
                .andReturn().getResponse().getContentAsString()).get(0);
        JsonNode track = json.readTree(mvc.perform(get("/api/parts/spin-tracks").header("Authorization", auth))
                .andReturn().getResponse().getContentAsString()).get(0);
        JsonNode tip = json.readTree(mvc.perform(get("/api/parts/tips").header("Authorization", auth))
                .andReturn().getResponse().getContentAsString()).get(0);

        String created = mvc.perform(post("/api/me/beyblades").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Erster\",\"fusionWheelId\":" + wheel.get("id")
                                + ",\"spinTrackId\":" + track.get("id") + ",\"tipId\":" + tip.get("id") + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long beybladeId = json.readTree(created).get("id").asLong();

        mvc.perform(post("/api/battles/quick").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"beybladeId\":" + beybladeId + "}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/leaderboard").header("Authorization", auth))
                .andExpect(jsonPath("$.me.username").value("spieler1"));
    }

    @Test
    void fremdeKreiselLassenSichNichtNutzen() throws Exception {
        String token = register("spieler2");
        mvc.perform(post("/api/battles/quick").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"beybladeId\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void abmeldenMachtTokenUngueltig() throws Exception {
        String token = register("spieler3");
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void falschesPasswortWirdAbgelehntUndTokenLiegtNurGehashtVor() throws Exception {
        String token = register("spieler4");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"spieler4\",\"password\":\"falsch999\"}"))
                .andExpect(status().isUnauthorized());

        assertTrue(tokens.findAll().stream().noneMatch(t -> t.getTokenHash().equals(token)));
        assertTrue(tokens.findAll().stream().allMatch(t -> t.getTokenHash().length() == 64));
    }
}
