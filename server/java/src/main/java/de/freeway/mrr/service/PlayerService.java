package de.freeway.mrr.service;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.freeway.mrr.exception.NotFoundException;
import de.freeway.mrr.exception.PlayerAlreadyExistsException;
import de.freeway.mrr.exception.ValidationFailedException;
import de.freeway.mrr.model.Account;
import de.freeway.mrr.model.Player;
import de.freeway.mrr.repository.PlayerRepository;
import de.freeway.mrr.repository.PlayerStateRepository;
import de.freeway.mrr.util.TimeUtil;

/**
 * Ownership-scoped player logic for MRR v0.3: every player belongs to exactly
 * one account and is derived server-side from the session — clients can never
 * set or read foreign {@code account_id} values.
 */
@Service
public class PlayerService {

    private final PlayerRepository players;
    private final PlayerStateRepository states;

    public PlayerService(PlayerRepository players, PlayerStateRepository states) {
        this.players = players;
        this.states = states;
    }

    @Transactional
    public Player createOwned(Account account, String playerId, String displayName) {
        validate(playerId, displayName);
        if (players.existsByPlayerId(playerId)) {
            throw new PlayerAlreadyExistsException("player_id '" + playerId + "' already exists");
        }
        try {
            return players.save(new Player(account, playerId, displayName, TimeUtil.now()));
        } catch (DataIntegrityViolationException ex) {
            throw new PlayerAlreadyExistsException("player_id '" + playerId + "' already exists");
        }
    }

    @Transactional(readOnly = true)
    public List<Player> listOwned(Account account) {
        return players.findByAccountOrderByIdAsc(account);
    }

    /** The caller's own player or 404 — foreign players are indistinguishable from missing ones. */
    @Transactional(readOnly = true)
    public Player getOwned(Account account, String playerId) {
        validatePlayerId(playerId);
        return players.findByAccountAndPlayerId(account, playerId)
                .orElseThrow(() -> notFound(playerId));
    }

    @Transactional
    public void deleteOwned(Account account, String playerId) {
        Player player = getOwned(account, playerId);
        // Remove the save state first — player_state.player_id is a foreign
        // key to players.id (deleting the player cascades nowhere).
        states.findByPlayerId(player.getId()).ifPresent(states::delete);
        players.delete(player);
    }

    /** The account's first player (used by profile and player state), or {@code null}. */
    @Transactional(readOnly = true)
    public Player primaryPlayer(Account account) {
        return players.findFirstByAccountOrderByIdAsc(account).orElse(null);
    }

    private static NotFoundException notFound(String playerId) {
        return new NotFoundException("player '" + playerId + "' not found");
    }

    private static void validatePlayerId(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            throw new ValidationFailedException("player_id must be a non-empty string");
        }
    }

    private static void validate(String playerId, String displayName) {
        validatePlayerId(playerId);
        if (displayName == null || displayName.isBlank()) {
            throw new ValidationFailedException("display_name must be a non-empty string");
        }
    }
}
