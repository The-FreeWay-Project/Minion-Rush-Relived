package de.freeway.mrr.admin;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.freeway.mrr.admin.dto.NewestPlayerDto;
import de.freeway.mrr.admin.dto.PlayerStatsDto;
import de.freeway.mrr.model.Player;
import de.freeway.mrr.repository.PlayerRepository;

@Service
public class PlayerAdminService {

    private final PlayerRepository playerRepository;
    private final ActivePlayerTracker activePlayerTracker;

    public PlayerAdminService(PlayerRepository playerRepository, ActivePlayerTracker activePlayerTracker) {
        this.playerRepository = playerRepository;
        this.activePlayerTracker = activePlayerTracker;
    }

    public List<NewestPlayerDto> getNewestPlayers(int limit) {
        int effectiveLimit = Math.min(Math.max(limit, 1), 50);
        return playerRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(effectiveLimit)
                .map(this::toNewestDto)
                .toList();
    }

    public PlayerStatsDto getPlayerStats() {
        List<Player> allPlayers = playerRepository.findAll();
        int total = allPlayers.size();
        int active = activePlayerTracker.getActiveCount();

        Instant now = Instant.now();
        Instant todayStart = now.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant weekStart = todayStart.minusSeconds(7 * 24 * 60 * 60L);

        long newToday = allPlayers.stream()
                .filter(p -> p.getCreatedAt() != null && parseInstant(p.getCreatedAt()).isAfter(todayStart))
                .count();
        long newThisWeek = allPlayers.stream()
                .filter(p -> p.getCreatedAt() != null && parseInstant(p.getCreatedAt()).isAfter(weekStart))
                .count();

        String latestRegistration = allPlayers.isEmpty() ? "Never" :
                allPlayers.stream()
                        .max((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                        .map(p -> formatInstant(parseInstant(p.getCreatedAt())))
                        .orElse("Unknown");

        return new PlayerStatsDto(total, active, (int) newToday, (int) newThisWeek, latestRegistration);
    }

    private NewestPlayerDto toNewestDto(Player player) {
        return new NewestPlayerDto(
                player.getId(),
                player.getPlayerId(),
                player.getDisplayName(),
                player.getCreatedAt(),
                player.getCreatedAt());
    }

    private Instant parseInstant(String isoString) {
        try {
            return Instant.from(DateTimeFormatter.ISO_INSTANT.parse(isoString));
        } catch (Exception e) {
            return Instant.EPOCH;
        }
    }

    private String formatInstant(Instant instant) {
        return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
                .withZone(ZoneOffset.UTC)
                .format(instant);
    }
}
