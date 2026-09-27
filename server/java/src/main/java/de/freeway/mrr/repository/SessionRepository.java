package de.freeway.mrr.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import de.freeway.mrr.model.Session;

public interface SessionRepository extends JpaRepository<Session, Long> {

    Optional<Session> findByTokenHash(String tokenHash);

    void deleteByTokenHash(String tokenHash);
}
