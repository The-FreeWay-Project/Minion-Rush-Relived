package de.freeway.mrr.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import de.freeway.mrr.model.Account;
import de.freeway.mrr.model.Player;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    Optional<Player> findByPlayerId(String playerId);

    boolean existsByPlayerId(String playerId);

    List<Player> findByAccountOrderByIdAsc(Account account);

    Optional<Player> findByAccountAndPlayerId(Account account, String playerId);

    Optional<Player> findFirstByAccountOrderByIdAsc(Account account);
}
