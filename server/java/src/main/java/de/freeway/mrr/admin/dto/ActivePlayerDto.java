package de.freeway.mrr.admin.dto;

public record ActivePlayerDto(
        Long playerId,
        String playerIdString,
        String displayName,
        String username,
        String lastActivity,
        String sessionStarted
) {
}
