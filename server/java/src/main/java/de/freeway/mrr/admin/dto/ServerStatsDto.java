package de.freeway.mrr.admin.dto;

public record ServerStatsDto(
        double cpuUsagePercent,
        long ramUsedBytes,
        long ramTotalBytes,
        String ramUsedFormatted,
        String ramTotalFormatted,
        int availableProcessors,
        double systemLoadAverage
) {
}
