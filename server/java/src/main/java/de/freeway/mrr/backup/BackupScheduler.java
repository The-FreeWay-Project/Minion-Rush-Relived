package de.freeway.mrr.backup;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Duration;

/**
 * Spring-kompatibler Scheduler für die Backup-Intervalle.
 * Kann in einer Spring-Boot-Annotated Klasse verwendet werden.
 * Alternativ kann die run()-Methode auch direkt aufgerufen werden.
 */
@Component
public class BackupScheduler {

    private final BackupService backupService;
    private final Duration dbInterval;
    private final Duration fullInterval;

    public BackupScheduler(BackupService service,
                           Duration dbInterval,
                           Duration fullInterval) {
        this.backupService = service;
        this.dbInterval = dbInterval;
        this.fullInterval = fullInterval;
    }

    // DB-Backup alle 15 Minuten
    @Scheduled(fixedRate = 900000)
    public void runDbBackup() {
        backupService.dbBackupRunnable().run();
    }

    // Full-Backup alle 30 Minuten
    @Scheduled(fixedRate = 1800000)
    public void runFullBackup() {
        backupService.fullBackupRunnable().run();
    }

    // Möglichkeit, beide manuell auszulösen (z.B. für Tests)
    public void runAll() {
        runDbBackup();
        runFullBackup();
    }
}