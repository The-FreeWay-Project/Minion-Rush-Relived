package de.freeway.mrr.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * Profile contract tests: account data plus the primary player, with
 * level/coins composed from player_state (the authoritative save state).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProfileApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void profileRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.detail").value("Not authenticated"));
    }

    @Test
    void profileContainsAccountAndDefaultPlayer() throws Exception {
        String username = "user-" + UUID.randomUUID().toString().substring(0, 8);
        Token token = registerAndLogin(username);

        mockMvc.perform(get("/api/v1/profile").header("Authorization", token.header()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account_id").isNumber())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.player.player_id").isString())
                .andExpect(jsonPath("$.player.display_name").value(username))
                .andExpect(jsonPath("$.player.level").value(1))
                .andExpect(jsonPath("$.player.coins").value(0));
    }

    @Test
    void profileLevelAndCoinsComeFromPlayerState() throws Exception {
        Token token = registerAndLogin("user-" + UUID.randomUUID().toString().substring(0, 8));

        mockMvc.perform(put("/api/v1/player/state")
                        .header("Authorization", token.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"experience\":777,\"level\":12,\"coins\":3456}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/profile").header("Authorization", token.header()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.player.level").value(12))
                .andExpect(jsonPath("$.player.coins").value(3456));
    }

    @Test
    void profileIsNullPlayerWhenAccountHasNoPlayer() throws Exception {
        Token token = registerAndLogin("user-" + UUID.randomUUID().toString().substring(0, 8));
        String defaultPlayerId = firstPlayerId(token);

        mockMvc.perform(delete("/api/v1/players/" + defaultPlayerId)
                        .header("Authorization", token.header()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/profile").header("Authorization", token.header()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.player").doesNotExist());
    }

    private String firstPlayerId(Token token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/players").header("Authorization", token.header()))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        int start = body.indexOf("\"player_id\":\"") + "\"player_id\":\"".length();
        if (start < "\"player_id\":\"".length()) {
            throw new IllegalStateException("account has no players: " + body);
        }
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }

    private Token registerAndLogin(String username) throws Exception {
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

    private record Token(String value) {
        String header() {
            return "Bearer " + value;
        }
    }
}
