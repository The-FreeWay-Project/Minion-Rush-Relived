package de.freeway.mrr.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Player API contract tests (MRR v0.3): every route requires auth, the owner
 * is derived server-side from the session, and foreign players behave exactly
 * like missing ones (404).
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlayerApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createReturns201WithOwnedPlayer() throws Exception {
        Token token = registerAndLogin();
        String playerId = uniquePlayerId();

        mockMvc.perform(post("/api/v1/players")
                        .header("Authorization", token.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"player_id\":\"" + playerId + "\",\"display_name\":\"Dave\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.player_id").value(playerId))
                .andExpect(jsonPath("$.display_name").value("Dave"))
                .andExpect(jsonPath("$.created_at").isString());
    }

    @Test
    void listReturnsOnlyOwnPlayers() throws Exception {
        Token owner = registerAndLogin();
        Token other = registerAndLogin();
        String ownPlayer = uniquePlayerId();
        String foreignPlayer = uniquePlayerId();
        createPlayer(owner, ownPlayer, "Own");
        createPlayer(other, foreignPlayer, "Foreign");

        mockMvc.perform(get("/api/v1/players").header("Authorization", owner.header()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].player_id").value(org.hamcrest.Matchers.hasItem(ownPlayer)))
                .andExpect(jsonPath("$[*].player_id")
                        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(foreignPlayer))));
    }

    @Test
    void getReturnsOwnPlayerAnd404ForForeignOrMissingOnes() throws Exception {
        Token owner = registerAndLogin();
        Token other = registerAndLogin();
        String ownPlayer = uniquePlayerId();
        String foreignPlayer = uniquePlayerId();
        createPlayer(owner, ownPlayer, "Own");
        createPlayer(other, foreignPlayer, "Foreign");

        mockMvc.perform(get("/api/v1/players/" + ownPlayer).header("Authorization", owner.header()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.player_id").value(ownPlayer));

        mockMvc.perform(get("/api/v1/players/" + foreignPlayer).header("Authorization", owner.header()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("player '" + foreignPlayer + "' not found"));

        mockMvc.perform(get("/api/v1/players/does-not-exist").header("Authorization", owner.header()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("player 'does-not-exist' not found"));
    }

    @Test
    void createRejectsDuplicatePlayerIdWith409() throws Exception {
        Token token = registerAndLogin();
        String playerId = uniquePlayerId();
        createPlayer(token, playerId, "First");

        mockMvc.perform(post("/api/v1/players")
                        .header("Authorization", token.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"player_id\":\"" + playerId + "\",\"display_name\":\"Second\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("player_id '" + playerId + "' already exists"));
    }

    @Test
    void createValidatesInputWith422() throws Exception {
        Token token = registerAndLogin();

        mockMvc.perform(post("/api/v1/players")
                        .header("Authorization", token.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"player_id\":\"\",\"display_name\":\"Dave\"}"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post("/api/v1/players")
                        .header("Authorization", token.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"player_id\":\"ok-id\",\"display_name\":\"  \"}"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post("/api/v1/players")
                        .header("Authorization", token.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void playerRoutesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/players"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.detail").value("Not authenticated"));

        mockMvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"player_id\":\"x\",\"display_name\":\"y\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/players")
                        .header("Authorization", "Bearer bogus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"player_id\":\"x\",\"display_name\":\"y\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid or expired session token"));
    }

    @Test
    void deleteRemovesOwnPlayerButNeverForeignOnes() throws Exception {
        Token owner = registerAndLogin();
        Token other = registerAndLogin();
        String ownPlayer = uniquePlayerId();
        String foreignPlayer = uniquePlayerId();
        createPlayer(owner, ownPlayer, "Own");
        createPlayer(other, foreignPlayer, "Foreign");

        mockMvc.perform(delete("/api/v1/players/" + foreignPlayer).header("Authorization", owner.header()))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/players/" + ownPlayer).header("Authorization", owner.header()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/players/" + ownPlayer).header("Authorization", owner.header()))
                .andExpect(status().isNotFound());

        // The foreign player must still exist for its owner.
        mockMvc.perform(get("/api/v1/players/" + foreignPlayer).header("Authorization", other.header()))
                .andExpect(status().isOk());
    }

    @Test
    void deleteUnknownPlayerReturns404() throws Exception {
        Token token = registerAndLogin();

        mockMvc.perform(delete("/api/v1/players/nope").header("Authorization", token.header()))
                .andExpect(status().isNotFound());
    }

    private void createPlayer(Token token, String playerId, String displayName) throws Exception {
        mockMvc.perform(post("/api/v1/players")
                        .header("Authorization", token.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"player_id\":\"" + playerId + "\",\"display_name\":\"" + displayName + "\"}"))
                .andExpect(status().isCreated());
    }

    private Token registerAndLogin() throws Exception {
        String username = "user-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"pw\"}"))
                .andExpect(status().isCreated());
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"pw\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        int start = body.indexOf("\"access_token\":\"") + "\"access_token\":\"".length();
        int end = body.indexOf('"', start);
        return new Token(body.substring(start, end));
    }

    private static String uniquePlayerId() {
        return "p-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private record Token(String value) {
        String header() {
            return "Bearer " + value;
        }
    }
}
