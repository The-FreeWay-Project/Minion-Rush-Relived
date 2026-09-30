package de.freeway.mrr.admin.dto;

public record PlayerStatsDto(
        int totalPlayers,
        int activePlayers,
        int newPlayersToday,
        int newPlayersThisWeek,
        String latestRegistration
) {
}
