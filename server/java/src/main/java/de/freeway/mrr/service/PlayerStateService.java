package de.freeway.mrr.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.freeway.mrr.exception.NotFoundException;
import de.freeway.mrr.model.Player;
import de.freeway.mrr.model.PlayerState;
import de.freeway.mrr.repository.PlayerStateRepository;
import de.freeway.mrr.util.TimeUtil;

/**
 * Read and update the save state of a player. The {@code player_state} row is
 * the single authoritative source for experience, level and coins (MRR v0.3);
 * missing rows are created with defaults (experience 0, level 1, coins 0).
 *
 * <p>Ownership rule: controllers only ever pass the caller's own primary
 * player — there is no way to read or write another account's state.</p>
 */
@Service
public class PlayerStateService {

    private final PlayerStateRepository states;

    public PlayerStateService(PlayerStateRepository states) {
        this.states = states;
    }

    /**
     * Save state for the account's primary player (created with defaults when
     * missing). Raises 404 when the account has no player at all.
     */
    @Transactional
    public PlayerState getState(Player primaryPlayer) {
        requirePlayer(primaryPlayer);
        return getOrCreate(primaryPlayer);
    }

    /**
     * Replace the save state values of the account's primary player.
     * Raises 404 when the account has no player at all.
     */
    @Transactional
    public PlayerState updateState(Player primaryPlayer, int experience, int level, int coins) {
        requirePlayer(primaryPlayer);
        PlayerState state = states.findByPlayerId(primaryPlayer.getId()).orElse(null);
        if (state == null) {
            state = new PlayerState(primaryPlayer, experience, level, coins, TimeUtil.now());
            states.persist(state);
            return state;
        }
        // Managed entity inside this transaction — changes flush automatically.
        state.setExperience(experience);
        state.setLevel(level);
        state.setCoins(coins);
        state.setUpdatedAt(TimeUtil.now());
        return state;
    }

    /** Existing state for one player, created with defaults when missing. */
    @Transactional
    public PlayerState getOrCreate(Player player) {
        requirePlayer(player);
        PlayerState state = states.findByPlayerId(player.getId()).orElse(null);
        if (state != null) {
            return state;
        }
        state = new PlayerState(player, 0, 1, 0, TimeUtil.now());
        states.persist(state);
        return state;
    }

    private static void requirePlayer(Player player) {
        if (player == null) {
            throw new NotFoundException("account has no player");
        }
    }
}
