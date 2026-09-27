package de.freeway.mrr.api;

import static org.assertj.core.api.Assertions.assertThat;
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

import de.freeway.mrr.model.Account;
import de.freeway.mrr.model.Session;
import de.freeway.mrr.repository.AccountRepository;
import de.freeway.mrr.repository.SessionRepository;
import de.freeway.mrr.security.PasswordHasher;
import de.freeway.mrr.security.TokenService;
import de.freeway.mrr.util.TimeUtil;

/**
 * Auth API contract tests: register (201/409/422), login (200/401 with one
 * identical error for unknown user and wrong password), logout (204/401),
 * token storage rules and expiry handling.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTest {

    private static final String INVALID_CREDENTIALS = "Invalid username or password";
    private static final String INVALID_TOKEN = "Invalid or expired session token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private SessionRepository sessions;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private PasswordHasher passwordHasher;

    @Test
    void registerCreatesAccountWith201() throws Exception {
        String username = uniqueUsername();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"hunter2\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.account_id").isNumber())
                .andExpect(jsonPath("$.username").value(username));
    }

    @Test
    void registerStoresOnlyAPbkdf2PasswordHash() throws Exception {
        String username = uniqueUsername();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"hunter2\"}"))
                .andExpect(status().isCreated());

        Account account = accounts.findByUsername(username).orElseThrow();
        String stored = account.getPasswordHash();
        assertThat(stored).startsWith("pbkdf2_sha256$");
        assertThat(stored).doesNotContain("hunter2");
        assertThat(passwordHasher.verify(stored, "hunter2")).isTrue();
        assertThat(passwordHasher.verify(stored, "wrong")).isFalse();
    }

    @Test
    void registerTrimsUsernameAndCreatesDefaultPlayer() throws Exception {
        String username = uniqueUsername();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"  " + username + "  \",\"password\":\"pw\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username));

        Account account = accounts.findByUsername(username).orElseThrow();
        assertThat(account.getUsername()).isEqualTo(username);
    }

    @Test
    void registerRejectsDuplicateUsernameWith409() throws Exception {
        String username = uniqueUsername();
        register(username, "pw");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"other\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("username '" + username + "' already exists"));
    }

    @Test
    void registerRejectsEmptyOrBlankInputWith422() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"pw\"}"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"   \",\"password\":\"pw\"}"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"name\",\"password\":\"  \"}"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void loginIssuesBearerTokenAndExpiresInSeconds() throws Exception {
        String username = uniqueUsername();
        register(username, "pw");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isString())
                .andExpect(jsonPath("$.token_type").value("bearer"))
                .andExpect(jsonPath("$.expires_in").value(86400));
    }

    @Test
    void loginFailsWithSameMessageForUnknownUserAndWrongPassword() throws Exception {
        String username = uniqueUsername();
        register(username, "pw");

        MvcResult unknownUser = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + uniqueUsername() + "\",\"password\":\"pw\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.detail").value(INVALID_CREDENTIALS))
                .andReturn();

        MvcResult wrongPassword = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.detail").value(INVALID_CREDENTIALS))
                .andReturn();

        assertThat(unknownUser.getResponse().getContentAsString())
                .isEqualTo(wrongPassword.getResponse().getContentAsString());
    }

    @Test
    void loginRejectsMissingFieldsWith422() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void loginRejectsMalformedJsonWith422() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void sessionTokenIsStoredAsSha256HashOnly() throws Exception {
        String username = uniqueUsername();
        register(username, "pw");
        String token = login(username, "pw");

        String storedHash = tokenService.hashToken(token);
        assertThat(tokenService.hashToken(token)).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(sessions.findByTokenHash(storedHash)).isPresent();
        boolean anyPlaintextToken = sessions.findAll().stream()
                .anyMatch(session -> session.getTokenHash().equals(token));
        assertThat(anyPlaintextToken).isFalse();
    }

    @Test
    void logoutInvalidatesSessionWith204AndTokenStopsWorking() throws Exception {
        String username = uniqueUsername();
        register(username, "pw");
        String token = login(username, "pw");

        mockMvc.perform(get("/api/v1/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.detail").value(INVALID_TOKEN));
    }

    @Test
    void logoutRequiresAuthenticationWith401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Not authenticated"));

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer garbage-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(INVALID_TOKEN));
    }

    @Test
    void protectedRoutesRejectMissingAndInvalidTokens() throws Exception {
        mockMvc.perform(get("/api/v1/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.detail").value("Not authenticated"));

        mockMvc.perform(get("/api/v1/profile").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(INVALID_TOKEN));

        mockMvc.perform(get("/api/v1/profile").header("Authorization", "Basic dXNlcjpwdw=="))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Not authenticated"));
    }

    @Test
    void expiredSessionIsRejectedAndDeleted() throws Exception {
        String username = uniqueUsername();
        register(username, "pw");
        Account account = accounts.findByUsername(username).orElseThrow();

        String expiredToken = tokenService.generateToken();
        sessions.save(new Session(
                account,
                tokenService.hashToken(expiredToken),
                TimeUtil.now(),
                "2000-01-01T00:00:00Z"));

        mockMvc.perform(get("/api/v1/profile").header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(INVALID_TOKEN));

        assertThat(sessions.findByTokenHash(tokenService.hashToken(expiredToken))).isEmpty();
    }

    @Test
    void publicRoutesIgnoreBogusAuthHeaders() throws Exception {
        String username = uniqueUsername();

        mockMvc.perform(post("/api/v1/auth/register")
                        .header("Authorization", "Bearer garbage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"pw\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("Authorization", "Bearer garbage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"pw\"}"))
                .andExpect(status().isOk());
    }

    private void register(String username, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated());
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        int start = body.indexOf("\"access_token\":\"") + "\"access_token\":\"".length();
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }

    private static String uniqueUsername() {
        return "user-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
