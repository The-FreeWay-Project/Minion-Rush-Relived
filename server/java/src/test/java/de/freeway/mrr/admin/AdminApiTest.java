package de.freeway.mrr.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import de.freeway.mrr.security.PasswordHasher;

@SpringBootTest
@AutoConfigureMockMvc
class AdminApiTest {

    @Autowired
    private MockMvc mockMvc;

    @TempDir
    Path tempDir;

    @Test
    void adminApiRejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/admin/status"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/stats"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/specs"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/backups"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/backups/status"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/admin/backups/database"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/admin/backups/full"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/admin/backups/verify"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminLoginRejectsWrongPassword() throws Exception {
        mockMvc.perform(post("/api/v1/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrongpassword\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid credentials"));
    }

    @Test
    void adminLoginRejectsWrongUsername() throws Exception {
        mockMvc.perform(post("/api/v1/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"notadmin\",\"password\":\"somepassword\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid credentials"));
    }

    @Test
    void adminLoginRejectsMissingFields() throws Exception {
        mockMvc.perform(post("/api/v1/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminLoginSuccessWithCorrectPassword() throws Exception {
        String password = "test-admin-password-123";
        String hash = new PasswordHasher(10_000).hash(password);

        Path adminFile = tempDir.resolve("admin.hash");
        Files.writeString(adminFile, hash);

        MvcResult result = mockMvc.perform(post("/api/v1/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains("MRR_ADMIN_SESSION");
    }

    @Test
    void adminLoginFailsWithWrongPassword() throws Exception {
        String password = "test-admin-password-123";
        String hash = new PasswordHasher(10_000).hash(password);

        Path adminFile = tempDir.resolve("admin.hash");
        Files.writeString(adminFile, hash);

        mockMvc.perform(post("/api/v1/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrongpassword\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid credentials"));
    }

    @Test
    void passwordIsStoredAsHashOnly() throws Exception {
        String password = "my-secret-password";
        PasswordHasher hasher = new PasswordHasher(10_000);
        String hash = hasher.hash(password);

        assertThat(hash).startsWith("pbkdf2_sha256$");
        assertThat(hash).doesNotContain(password);
        assertThat(hasher.verify(hash, password)).isTrue();
        assertThat(hasher.verify(hash, "wrong")).isFalse();
    }

    @Test
    void adminSessionManagerCreatesAndValidatesSessions() {
        AdminSessionManager manager = new AdminSessionManager(1000L);
        String token = manager.createSession();

        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
        assertThat(manager.isValid(token)).isTrue();
    }

    @Test
    void adminSessionManagerRejectsInvalidTokens() {
        AdminSessionManager manager = new AdminSessionManager();
        assertThat(manager.isValid(null)).isFalse();
        assertThat(manager.isValid("")).isFalse();
        assertThat(manager.isValid("non-existent-token")).isFalse();
    }

    @Test
    void adminSessionManagerInvalidatesSessions() {
        AdminSessionManager manager = new AdminSessionManager();
        String token = manager.createSession();
        assertThat(manager.isValid(token)).isTrue();

        manager.invalidate(token);
        assertThat(manager.isValid(token)).isFalse();
    }
}
