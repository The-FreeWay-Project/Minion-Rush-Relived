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
 * Save-state contract tests: player_state is the single authoritative source
 * for experience/level/coins, addressable only through the caller's own
 * account (MRR v0.3).
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlayerStateApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getStateCreatesDefaultStateForFreshAccount() throws Exception {
        Token token = registerAndLogin();

        mockMvc.perform(get("/api/v1/player/state").header("Authorization", token.header()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.player_id").isNumber())
                .andExpect(jsonPath("$.experience").value(0))
                .andExpect(jsonPath("$.level").value(1))
                .andExpect(jsonPath("$.coins").value(0))
                .andExpect(jsonPath("$.updated_at").isString());
    }

    @Test
    void putStateIsPersistedAndReturnedByGet() throws Exception {
        Token token = registerAndLogin();

        mockMvc.perform(put("/api/v1/player/state")
                        .header("Authorization", token.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"experience\":1234,\"level\":7,\"coins\":999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.experience").value(1234))
                .andExpect(jsonPath("$.level").value(7))
                .andExpect(jsonPath("$.coins").value(999));

        mockMvc.perform(get("/api/v1/player/state").header("Authorization", token.header()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.experience").value(1234))
                .andExpect(jsonPath("$.level").value(7))
                .andExpect(jsonPath("$.coins").value(999));
    }

    @Test
    void stateIsIsolatedBetweenAccounts() throws Exception {
        Token writer = registerAndLogin();
        Token reader = registerAndLogin();

        mockMvc.perform(put("/api/v1/player/state")
                        .header("Authorization", writer.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"experience\":500,\"level\":5,\"coins\":42}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/player/state").header("Authorization", reader.header()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.experience").value(0))
                .andExpect(jsonPath("$.level").value(1))
                .andExpect(jsonPath("$.coins").value(0));
    }

    @Test
    void putValidatesValuesWith422() throws Exception {
        Token token = registerAndLogin();

        mockMvc.perform(put("/api/v1/player/state")
                        .header("Authorization", token.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"experience\":-1,\"level\":1,\"coins\":0}"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(put("/api/v1/player/state")
                        .header("Authorization", token.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"experience\":0,\"level\":0,\"coins\":0}"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(put("/api/v1/player/state")
                        .header("Authorization", token.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"experience\":0,\"level\":1,\"coins\":-5}"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(put("/api/v1/player/state")
                        .header("Authorization", token.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"experience\":0,\"coins\":5}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void stateRoutesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/player/state"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.detail").value("Not authenticated"));

        mockMvc.perform(put("/api/v1/player/state")
                        .header("Authorization", "Bearer bogus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"experience\":1,\"level\":1,\"coins\":1}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid or expired session token"));
    }

    @Test
    void stateWithoutPlayerReturns404() throws Exception {
        Token token = registerAndLogin();
        String defaultPlayerId = firstPlayerId(token);

        mockMvc.perform(delete("/api/v1/players/" + defaultPlayerId)
                        .header("Authorization", token.header()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/player/state").header("Authorization", token.header()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("account has no player"));

        mockMvc.perform(put("/api/v1/player/state")
                        .header("Authorization", token.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"experience\":1,\"level\":1,\"coins\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("account has no player"));
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

    private record Token(String value) {
        String header() {
            return "Bearer " + value;
        }
    }
}
