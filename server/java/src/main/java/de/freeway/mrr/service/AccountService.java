package de.freeway.mrr.service;

import java.security.SecureRandom;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.freeway.mrr.exception.AccountAlreadyExistsException;
import de.freeway.mrr.exception.ValidationFailedException;
import de.freeway.mrr.model.Account;
import de.freeway.mrr.model.Player;
import de.freeway.mrr.repository.AccountRepository;
import de.freeway.mrr.repository.PlayerRepository;
import de.freeway.mrr.security.PasswordHasher;
import de.freeway.mrr.util.TimeUtil;

/**
 * Registration business logic. Mirrors the Python MRR server: the username is
 * trimmed, the password is stored only as a PBKDF2 hash, and every new
 * account automatically gets one default owned player.
 */
@Service
public class AccountService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accounts;
    private final PlayerRepository players;
    private final PasswordHasher passwordHasher;

    public AccountService(AccountRepository accounts, PlayerRepository players, PasswordHasher passwordHasher) {
        this.accounts = accounts;
        this.players = players;
        this.passwordHasher = passwordHasher;
    }

    @Transactional
    public Account register(String username, String password) {
        String normalizedUsername = validate(username, password);
        if (accounts.existsByUsername(normalizedUsername)) {
            throw new AccountAlreadyExistsException("username '" + normalizedUsername + "' already exists");
        }
        Account account;
        try {
            account = accounts.save(new Account(normalizedUsername, passwordHasher.hash(password), TimeUtil.now()));
        } catch (DataIntegrityViolationException ex) {
            throw new AccountAlreadyExistsException("username '" + normalizedUsername + "' already exists");
        }
        createDefaultPlayer(account);
        return account;
    }

    private static String validate(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new ValidationFailedException("username must be a non-empty string");
        }
        if (password == null || password.isBlank()) {
            throw new ValidationFailedException("password must be a non-empty string");
        }
        return username.trim();
    }

    private void createDefaultPlayer(Account account) {
        String playerId = String.format("player-%04d", account.getId());
        if (players.existsByPlayerId(playerId)) {
            byte[] suffix = new byte[3];
            RANDOM.nextBytes(suffix);
            playerId = playerId + "-" + toHex(suffix);
        }
        try {
            players.save(new Player(account, playerId, account.getUsername(), TimeUtil.now()));
        } catch (DataIntegrityViolationException ex) {
            // Extremely unlikely collision — the account stays usable and the
            // profile simply has no player yet (same as the Python server).
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
