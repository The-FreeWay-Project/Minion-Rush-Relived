package de.freeway.mrr.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import de.freeway.mrr.model.Account;
import de.freeway.mrr.model.Player;

class ActivePlayerTrackerTest {

    @Test
    void newTrackerHasNoActivePlayers() {
        ActivePlayerTracker tracker = new ActivePlayerTracker();
        assertThat(tracker.getActiveCount()).isZero();
    }

    @Test
    void recordActivityAddsPlayer() {
        ActivePlayerTracker tracker = new ActivePlayerTracker();
        Account account = new Account("testuser", "hash", Instant.now().toString());
        Player player = new Player(account, "player-001", "TestPlayer", Instant.now().toString());

        tracker.recordActivity(account, player);

        assertThat(tracker.getActiveCount()).isEqualTo(1);
    }

    @Test
    void multiplePlayersAreTracked() {
        ActivePlayerTracker tracker = new ActivePlayerTracker();
        Account account = new Account("user1", "hash", Instant.now().toString());
        Player player1 = new Player(account, "player-001", "Player1", Instant.now().toString());
        Player player2 = new Player(account, "player-002", "Player2", Instant.now().toString());

        tracker.recordActivity(account, player1);
        tracker.recordActivity(account, player2);

        assertThat(tracker.getActiveCount()).isEqualTo(2);
    }

    @Test
    void cleanupInactiveRemovesOldPlayers() throws InterruptedException {
        ActivePlayerTracker tracker = new ActivePlayerTracker(50);
        Account account = new Account("testuser", "hash", Instant.now().toString());
        Player player = new Player(account, "player-001", "TestPlayer", Instant.now().toString());

        tracker.recordActivity(account, player);
        assertThat(tracker.getActiveCount()).isEqualTo(1);

        Thread.sleep(100);
        tracker.cleanupInactive();

        assertThat(tracker.getActiveCount()).isZero();
    }

    @Test
    void nullAccountOrPlayerDoesNotThrow() {
        ActivePlayerTracker tracker = new ActivePlayerTracker();
        tracker.recordActivity(null, null);
        assertThat(tracker.getActiveCount()).isZero();
    }
}
