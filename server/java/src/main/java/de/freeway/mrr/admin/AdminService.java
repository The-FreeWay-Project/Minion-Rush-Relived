package de.freeway.mrr.admin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.freeway.mrr.admin.dto.BackupInfoDto;
import de.freeway.mrr.admin.dto.BackupStatusDto;
import de.freeway.mrr.admin.dto.CommandResponseDto;
import de.freeway.mrr.backup.BackupManifest;
import de.freeway.mrr.backup.BackupService;
import de.freeway.mrr.backup.BackupStorage;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final BackupService backupService;
    private final ServerStatsService statsService;
    private final String backupTarget;
    private final int retentionDays;
    private final boolean backupEnabled;

    public AdminService(
            BackupService backupService,
            ServerStatsService statsService,
            @Value("${mrr.backup.target:./backups}") String backupTarget,
            @Value("${mrr.backup.retention-days:30}") int retentionDays,
            @Value("${mrr.backup.enabled:true}") boolean backupEnabled) {
        this.backupService = backupService;
        this.statsService = statsService;
        this.backupTarget = backupTarget;
        this.retentionDays = retentionDays;
        this.backupEnabled = backupEnabled;
    }

    public BackupStatusDto getBackupStatus() {
        List<BackupInfoDto> backups = listBackups();
        String lastBackup = backups.isEmpty() ? "Never" : backups.get(0).createdAt();
        String nextBackup = calculateNextBackup(backups);
        return new BackupStatusDto(backupEnabled, lastBackup, nextBackup, retentionDays, backups);
    }

    public List<BackupInfoDto> listBackups() {
        List<BackupInfoDto> result = new ArrayList<>();
        Path manifestsDir = Paths.get(backupTarget, "manifests");
        if (!Files.isDirectory(manifestsDir)) {
            return result;
        }
        try (var stream = Files.list(manifestsDir)) {
            List<Path> manifestFiles = stream
                    .filter(p -> p.toString().endsWith(".ser") || p.toString().endsWith(".manifest"))
                    .sorted(Comparator.comparingLong(this::getLastModified).reversed())
                    .toList();

            for (Path manifestPath : manifestFiles) {
                try (var in = Files.newInputStream(manifestPath)) {
                    BackupManifest manifest = (BackupManifest) new java.io.ObjectInputStream(in).readObject();
                    Path filePath = resolveBackupFile(manifest);
                    long size = Files.exists(filePath) ? Files.size(filePath) : 0;
                    result.add(new BackupInfoDto(
                            manifest.backupType(),
                            manifest.file(),
                            size,
                            ServerStatsService.formatBytes(size),
                            manifest.getCreatedAtHuman(),
                            manifest.sha256(),
                            manifest.status()));
                } catch (Exception e) {
                    log.debug("Skipping unreadable manifest: {}", manifestPath);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to list backups: {}", e.getMessage());
        }
        return result;
    }

    public CommandResponseDto triggerDatabaseBackup() {
        try {
            backupService.dbBackupRunnable().run();
            return new CommandResponseDto(true, "Database backup completed successfully");
        } catch (Exception e) {
            log.error("Database backup failed", e);
            return new CommandResponseDto(false, "Database backup failed: " + e.getMessage());
        }
    }

    public CommandResponseDto triggerFullBackup() {
        try {
            backupService.fullBackupRunnable().run();
            return new CommandResponseDto(true, "Full backup completed successfully");
        } catch (Exception e) {
            log.error("Full backup failed", e);
            return new CommandResponseDto(false, "Full backup failed: " + e.getMessage());
        }
    }

    public CommandResponseDto verifyBackups() {
        try {
            Path manifestsDir = Paths.get(backupTarget, "manifests");
            if (!Files.isDirectory(manifestsDir)) {
                return new CommandResponseDto(true, "No backups to verify");
            }
            int verified = 0;
            int failed = 0;
            try (var stream = Files.list(manifestsDir)) {
                for (Path manifestPath : stream.toList()) {
                    try {
                        BackupManifest result = backupService.verifyBackupManifest(manifestPath);
                        if ("verified".equals(result.status())) {
                            verified++;
                        } else {
                            failed++;
                        }
                    } catch (Exception e) {
                        failed++;
                    }
                }
            }
            return new CommandResponseDto(true,
                    "Verification complete: " + verified + " verified, " + failed + " failed");
        } catch (Exception e) {
            log.error("Backup verification failed", e);
            return new CommandResponseDto(false, "Verification failed: " + e.getMessage());
        }
    }

    private Path resolveBackupFile(BackupManifest manifest) {
        String fileName = manifest.file();
        if (manifest.backupType().equals("database")) {
            return Paths.get(backupTarget, "database", fileName);
        }
        return Paths.get(backupTarget, "full", fileName);
    }

    private long getLastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    private String calculateNextBackup(List<BackupInfoDto> backups) {
        if (!backupEnabled) {
            return "Disabled";
        }
        if (backups.isEmpty()) {
            return "On next scheduler tick";
        }
        try {
            String lastDate = backups.get(0).createdAt();
            Instant last = Instant.from(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
                    .withZone(ZoneOffset.UTC)
                    .parse(lastDate));
            Instant next = last.plusSeconds(15 * 60L);
            return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
                    .withZone(ZoneOffset.UTC)
                    .format(next);
        } catch (Exception e) {
            return "Unknown";
        }
    }
}
