package de.freeway.mrr.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.freeway.mrr.exception.AuthenticationFailedException;
import de.freeway.mrr.model.Account;
import de.freeway.mrr.model.Session;
import de.freeway.mrr.repository.AccountRepository;
import de.freeway.mrr.repository.SessionRepository;
import de.freeway.mrr.security.PasswordHasher;
import de.freeway.mrr.security.TokenService;
import de.freeway.mrr.util.TimeUtil;

/**
 * Login, session issuance, bearer-token authentication and logout — the Java
 * counterpart of the Python {@code AuthenticationService}.
 *
 * <ul>
 *   <li>Login failures use one identical message for unknown user and wrong
 *       password, so neither can be probed.</li>
 *   <li>Sessions store only the SHA-256 hash of the token.</li>
 *   <li>Expired sessions are deleted when they are first used.</li>
 * </ul>
 */
@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    static final String INVALID_CREDENTIALS = "Invalid username or password";
    static final String INVALID_TOKEN = "Invalid or expired session token";

    private final AccountRepository accounts;
    private final SessionRepository sessions;
    private final PasswordHasher passwordHasher;
    private final TokenService tokenService;
    private final long sessionTtlSeconds;

    public AuthenticationService(
            AccountRepository accounts,
            SessionRepository sessions,
            PasswordHasher passwordHasher,
            TokenService tokenService,
            @Value("${mrr.session-ttl-seconds:86400}") long sessionTtlSeconds) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
        this.sessionTtlSeconds = sessionTtlSeconds;
    }

    public long getSessionTtlSeconds() {
        return sessionTtlSeconds;
    }

    /** Result of a successful login: the raw token and its TTL in seconds. */
    public record LoginResult(String accessToken, long expiresIn) {
    }

    @Transactional
    public LoginResult login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw new AuthenticationFailedException(INVALID_CREDENTIALS);
        }
        Account account = accounts.findByUsername(username.trim()).orElse(null);
        if (account == null || !passwordHasher.verify(account.getPasswordHash(), password)) {
            throw new AuthenticationFailedException(INVALID_CREDENTIALS);
        }
        String token = tokenService.generateToken();
        sessions.save(new Session(
                account,
                tokenService.hashToken(token),
                TimeUtil.now(),
                TimeUtil.plusSeconds(sessionTtlSeconds)));
        return new LoginResult(token, sessionTtlSeconds);
    }

    /**
     * Resolve a bearer token to its account, or {@code null} when the token is
     * unknown, malformed or expired (expired sessions are deleted).
     */
    @Transactional
    public Account authenticateOrNull(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        Session session = sessions.findByTokenHash(tokenService.hashToken(token)).orElse(null);
        if (session == null) {
            return null;
        }
        if (TimeUtil.isExpired(session.getExpiresAt())) {
            log.debug("Deleting expired MRR session {}", session.getId());
            sessions.delete(session);
            return null;
        }
        return session.getAccount();
    }

    /** Like {@link #authenticateOrNull} but throws with the standard 401 detail. */
    @Transactional
    public Account authenticate(String token) {
        Account account = authenticateOrNull(token);
        if (account == null) {
            throw new AuthenticationFailedException(INVALID_TOKEN);
        }
        return account;
    }

    @Transactional
    public boolean logout(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        String hash = tokenService.hashToken(token);
        if (sessions.findByTokenHash(hash).isEmpty()) {
            return false;
        }
        sessions.deleteByTokenHash(hash);
        return true;
    }
}
