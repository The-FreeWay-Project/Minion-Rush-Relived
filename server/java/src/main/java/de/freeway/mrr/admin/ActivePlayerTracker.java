package de.freeway.mrr.admin;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import de.freeway.mrr.model.Account;
import de.freeway.mrr.model.Player;

@Component
public class ActivePlayerTracker {

    private final Map<String, ActivePlayerInfo> activePlayers = new ConcurrentHashMap<>();
    private final long inactivityTimeoutMillis;

    public ActivePlayerTracker() {
        this(300000L);
    }

    public ActivePlayerTracker(long inactivityTimeoutMillis) {
        this.inactivityTimeoutMillis = inactivityTimeoutMillis;
    }

    public void recordActivity(Account account, Player player) {
        if (account == null || player == null) return;
        String key = account.getId() + ":" + player.getId();
        activePlayers.put(key, new ActivePlayerInfo(
                player.getId(),
                player.getPlayerId(),
                player.getDisplayName(),
                account.getUsername(),
                Instant.now(),
                Instant.now()));
    }

    public int getActiveCount() {
        cleanupInactive();
        return activePlayers.size();
    }

    public Map<String, ActivePlayerInfo> getActivePlayers() {
        cleanupInactive();
        return new ConcurrentHashMap<>(activePlayers);
    }

    @Scheduled(fixedRate = 60000)
    public void cleanupInactive() {
        Instant cutoff = Instant.now().minusMillis(inactivityTimeoutMillis);
        activePlayers.entrySet().removeIf(entry -> entry.getValue().lastActivity().isBefore(cutoff));
    }

    public record ActivePlayerInfo(
            Long playerId,
            String playerIdString,
            String displayName,
            String username,
            Instant sessionStarted,
            Instant lastActivity) {
    }
}
