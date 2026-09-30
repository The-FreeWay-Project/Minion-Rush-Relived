package de.freeway.mrr.admin.dto;

public record NewestPlayerDto(
        Long id,
        String playerId,
        String displayName,
        String registeredAt,
        String lastLogin
) {
}
