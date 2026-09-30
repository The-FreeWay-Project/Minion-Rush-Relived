package de.freeway.mrr.backup;

import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * DTO für ein Backup-Manifest. Wird neben der Backup-Datei gespeichert.
 */
public record BackupManifest(
        String backupType,
        Instant createdAt,
        String file,
        long size,
        String sha256,
        String status
) implements Serializable {
    public BackupManifest(String backupType, String file, long size, String sha256) {
        this(backupType, Instant.now(), file, size, sha256, "verified");
    }

    public String getCreatedAtFormatted() {
        return DateTimeFormatter.ISO_INSTANT.format(createdAt);
    }

    public String getCreatedAtHuman() {
        return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
                .withZone(ZoneOffset.UTC)
                .format(createdAt);
    }
}