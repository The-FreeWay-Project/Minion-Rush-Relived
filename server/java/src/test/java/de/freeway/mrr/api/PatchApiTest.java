package de.freeway.mrr.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PatchApiTest {

    private static final String UP_TO_DATE = "MRR Patcher is up to date.";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void manifestReturnsCurrentDefaults() throws Exception {
        mockMvc.perform(get("/api/v1/patch/manifest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("A1.0.0"))
                .andExpect(jsonPath("$.channel").value("stable"))
                .andExpect(jsonPath("$.platform").value("android"))
                .andExpect(jsonPath("$.serverVersion").value("0.3.0"))
                .andExpect(jsonPath("$.message").value(UP_TO_DATE))
                .andExpect(jsonPath("$.files").isArray())
                .andExpect(jsonPath("$.files").isEmpty());
    }

    @Test
    void manifestIsPublicWithoutAuthorizationHeader() throws Exception {
        // No Authorization header is sent; a 200 proves the AuthFilter keeps
        // the patch endpoint public like /api/v1/health.
        mockMvc.perform(get("/api/v1/patch/manifest").param("installed", "A1.0.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(UP_TO_DATE));
    }

    @Test
    void manifestOffersUpdateForOlderInstalled() throws Exception {
        mockMvc.perform(get("/api/v1/patch/manifest").param("installed", "A0.9.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("A1.0.0"))
                .andExpect(jsonPath("$.message").value("Update to A1.0.0 available."));
    }

    @Test
    void manifestReportsAheadForNewerInstalled() throws Exception {
        mockMvc.perform(get("/api/v1/patch/manifest").param("installed", "A2.0.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Installed patcher version is ahead of the stable channel."));
    }

    @Test
    void manifestIgnoresUnparsableInstalled() throws Exception {
        mockMvc.perform(get("/api/v1/patch/manifest").param("installed", "banana"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(UP_TO_DATE));
    }

    @Test
    void wrongMethodReturns405() throws Exception {
        mockMvc.perform(post("/api/v1/patch/manifest"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.detail").value("Method Not Allowed"));
    }
}
