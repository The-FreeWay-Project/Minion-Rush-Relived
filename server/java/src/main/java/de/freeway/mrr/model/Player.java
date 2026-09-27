package de.freeway.mrr.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A synthetic test player owned by exactly one account. Level and coins do
 * not live here — {@link PlayerState} is the single authoritative source for
 * save-state values (MRR v0.3 data model).
 */
@Entity
@Table(name = "players")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "player_id", nullable = false, unique = true, length = 128)
    private String playerId;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "created_at", nullable = false, length = 40)
    private String createdAt;

    protected Player() {
    }

    public Player(Account account, String playerId, String displayName, String createdAt) {
        this.account = account;
        this.playerId = playerId;
        this.displayName = displayName;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public Long getAccountId() {
        return account == null ? null : account.getId();
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
