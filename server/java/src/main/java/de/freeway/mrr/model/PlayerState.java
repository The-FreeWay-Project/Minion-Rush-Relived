package de.freeway.mrr.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Persistent save state for one player. The primary key is the player's
 * internal id — there is no independent id. This table is the only
 * authoritative source for experience, level and coins.
 */
@Entity
@Table(name = "player_state")
public class PlayerState {

    @Id
    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Column(name = "experience", nullable = false)
    private int experience;

    @Column(name = "level", nullable = false)
    private int level;

    @Column(name = "coins", nullable = false)
    private int coins;

    @Column(name = "updated_at", nullable = false, length = 40)
    private String updatedAt;

    protected PlayerState() {
    }

    public PlayerState(Player player, int experience, int level, int coins, String updatedAt) {
        this.player = player;
        this.experience = experience;
        this.level = level;
        this.coins = coins;
        this.updatedAt = updatedAt;
    }

    public Player getPlayer() {
        return player;
    }

    public int getExperience() {
        return experience;
    }

    public void setExperience(int experience) {
        this.experience = experience;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getCoins() {
        return coins;
    }

    public void setCoins(int coins) {
        this.coins = coins;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PlayerState that)) {
            return false;
        }
        return playerId() != null && playerId().equals(that.playerId());
    }

    @Override
    public int hashCode() {
        return playerId() == null ? 0 : playerId().hashCode();
    }

    private Long playerId() {
        return player == null ? null : player.getId();
    }
}
