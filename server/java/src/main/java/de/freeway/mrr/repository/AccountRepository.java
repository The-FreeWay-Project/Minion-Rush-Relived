package de.freeway.mrr.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import de.freeway.mrr.model.Account;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUsername(String username);

    boolean existsByUsername(String username);
}
