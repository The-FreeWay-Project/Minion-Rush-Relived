package de.freeway.mrr.repository;

import java.util.Optional;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Repository;

import de.freeway.mrr.model.PlayerState;

/**
 * Repository for the {@code player_state} table. Implemented directly on the
 * {@link EntityManager} because the primary key is the player association
 * (player_id), which Spring Data's derived repositories do not support.
 */
@Repository
public class PlayerStateRepository {

    private final EntityManager entityManager;

    public PlayerStateRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Optional<PlayerState> findByPlayerId(Long playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(entityManager.find(PlayerState.class, playerId));
    }

    /** Inserts a new save-state row (the player id is already known). */
    public void persist(PlayerState state) {
        entityManager.persist(state);
    }

    public void delete(PlayerState state) {
        PlayerState managed = entityManager.find(PlayerState.class, state.getPlayer().getId());
        if (managed != null) {
            entityManager.remove(managed);
        }
    }
}
