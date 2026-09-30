package de.freeway.mrr.admin.dto;

import java.util.List;

public record BackupStatusDto(
        boolean enabled,
        String lastBackup,
        String nextBackup,
        int retentionDays,
        List<BackupInfoDto> backups
) {
}
