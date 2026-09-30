package de.freeway.mrr.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AdminPlayerApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void activePlayersEndpointRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/admin/players/active"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void newestPlayersEndpointRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/admin/players/newest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void playerStatsEndpointRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/admin/players/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void commandEndpointRequiresAuth() throws Exception {
        mockMvc.perform(post("/api/v1/admin/commands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"command\":\"/status\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void commandEndpointRejectsEmptyCommand() throws Exception {
        mockMvc.perform(post("/api/v1/admin/commands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"command\":\"\"}"))
                .andExpect(status().isUnauthorized());
    }
}
