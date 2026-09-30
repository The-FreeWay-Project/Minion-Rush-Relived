package de.freeway.mrr.admin.dto;

public record BackupInfoDto(
        String type,
        String fileName,
        long sizeBytes,
        String sizeFormatted,
        String createdAt,
        String sha256,
        String status
) {
}
