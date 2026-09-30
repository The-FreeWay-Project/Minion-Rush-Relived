package de.freeway.mrr.admin.dto;

public record ServerStatusDto(
        String status,
        String version,
        long uptimeSeconds,
        String uptimeFormatted
) {
}
