package de.freeway.mrr.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import de.freeway.mrr.backup.BackupScheduler;
import de.freeway.mrr.backup.BackupService;

@Configuration
@EnableScheduling
public class BackupConfig {

    @Bean
    public BackupService backupService(
            @Value("${mrr.data-dir:./data}") String dataDir,
            @Value("${mrr.backup.target:./backups}") String backupTarget) {
        Path serverRoot = Paths.get(dataDir).toAbsolutePath().normalize();
        Path backupBaseDir = Paths.get(backupTarget).toAbsolutePath().normalize();
        return new BackupService(serverRoot, backupBaseDir);
    }

    @Bean
    public BackupScheduler backupScheduler(
            BackupService backupService,
            @Value("${mrr.backup.db-backup-interval-minutes:15}") int dbInterval,
            @Value("${mrr.backup.full-backup-interval-minutes:30}") int fullInterval) {
        return new BackupScheduler(
                backupService,
                Duration.ofMinutes(dbInterval),
                Duration.ofMinutes(fullInterval));
    }
}
