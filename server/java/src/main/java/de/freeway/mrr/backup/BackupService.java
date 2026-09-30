package de.freeway.mrr.backup;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

public class BackupService {

    private final Path serverRoot;
    private final Path backupBaseDir;
    private final DatabaseBackupService dbSvc;
    private final FullBackupService fullSvc;

    private static final int DB_INTERVAL_MIN = 15;
    private static final int FULL_INTERVAL_MIN = 30;

    public BackupService(Path serverRoot, Path backupBaseDir) {
        this.serverRoot = serverRoot;
        this.backupBaseDir = backupBaseDir;
        this.dbSvc = new DatabaseBackupService(serverRoot.resolve("mrr.db"), backupBaseDir);
        this.fullSvc = new FullBackupService(serverRoot, backupBaseDir);
    }

    public Path getBackupBaseDir() { return backupBaseDir; }

    /** ---- Datenbank-Backup ---- */
    public Runnable dbBackupRunnable() {
        return () -> {
            try {
                Path backup = dbSvc.createBackup();
                String timestamp = java.time.Instant.now().atZone(java.time.ZoneOffset.UTC)
                        .format(java.time.format.DateTimeFormatter.ISO_INSTANT);
                BackupManifest manifest = new BackupManifest("database", backup.getFileName().toString(),
                        0, "placeholder-sha");
                Path manifestPath = new BackupStorage(backupBaseDir.toString()).manifestPath(backup.toString());
                try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(manifestPath))) {
                    oos.writeObject(manifest);
                }
                System.out.println("[Backup] DB-Backup erstellt: " + backup + " | Manifest: " + manifestPath);
            } catch (Exception e) {
                System.err.println("[Backup] Fehler beim DB-Backup: " + e.getMessage());
                e.printStackTrace();
            }
        };
    }

    /** ---- Full Backup ---- */
    public Runnable fullBackupRunnable() {
        return () -> {
            try {
                Path backup = fullSvc.createBackup();
                String timestamp = java.time.Instant.now().atZone(java.time.ZoneOffset.UTC)
                        .format(java.time.format.DateTimeFormatter.ISO_INSTANT);
                BackupManifest manifest = new BackupManifest("full", backup.getFileName().toString(),
                        0, "placeholder-sha");
                Path manifestPath = new BackupStorage(backupBaseDir.toString()).manifestPath(backup.toString());
                try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(manifestPath))) {
                    oos.writeObject(manifest);
                }
                System.out.println("[Backup] Full-Backup erstellt: " + backup + " | Manifest: " + manifestPath);
            } catch (Exception e) {
                System.err.println("[Backup] Fehler beim Full-Backup: " + e.getMessage());
                e.printStackTrace();
            }
        };
    }

    /** ---- SHA-256 Prüfung nach Transfer ---- */
    public BackupManifest verifyBackupManifest(Path manifestPath) {
        try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(manifestPath))) {
            BackupManifest manifest = (BackupManifest) ois.readObject();
            Path filePath = resolveManifestFilePath(manifest);
            if (filePath == null) {
                manifest = new BackupManifest(manifest.backupType(), manifest.createdAt(),
                        manifest.file(), manifest.size(), manifest.sha256(), "error-missing-file");
                return manifest;
            }
            String newHash = sha256File(filePath);
            if (newHash != null && newHash.equals(manifest.sha256())) {
                if (!"verified".equals(manifest.status())) {
                    manifest = new BackupManifest(manifest.backupType(), manifest.createdAt(),
                            manifest.file(), manifest.size(), manifest.sha256(), "verified");
                }
                return manifest;
            } else {
                manifest = new BackupManifest(manifest.backupType(), manifest.createdAt(),
                        manifest.file(), manifest.size(), newHash, "failed-hash-mismatch");
                return manifest;
            }
        } catch (Exception e) {
            return new BackupManifest("error", Instant.now(), "unknown", 0, "error", "parsing-error");
        }
    }

    private Path resolveManifestFilePath(BackupManifest manifest) {
        String fileName = manifest.file();
        if (manifest.backupType().equals("database")) {
            return backupBaseDir.resolve("database").resolve(fileName);
        } else {
            return backupBaseDir.resolve("full").resolve(fileName);
        }
    }

    private String sha256File(Path file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream fis = Files.newInputStream(file)) {
                byte[] data = new byte[8192];
                int read;
                while ((read = fis.read(data)) != -1) {
                    md.update(data, 0, read);
                }
            }
            byte[] hashBytes = md.digest();
            return java.util.Base64.getEncoder().encodeToString(hashBytes);
        } catch (Exception e) {
            return null;
        }
    }

    /** ---- Retention (AufrÃ¤umung) ---- */
    public void cleanupOldBackups(int retentionDays) {
        try {
            Instant cutoff = Instant.now().minusSeconds(retentionDays * 24L * 60 * 60);
            Path manifestsDir = backupBaseDir.resolve("manifests");
            if (!Files.exists(manifestsDir)) return;
            try (var stream = Files.list(manifestsDir)) {
                for (Path manifestPath : stream.toList()) {
                    try (var in = Files.newInputStream(manifestPath)) {
                        BackupManifest m = (BackupManifest) new ObjectInputStream(in).readObject();
                        if (m.createdAt().isBefore(cutoff)) {
                            boolean onlyValid = isOnlyValidBackupOfType(m.backupType());
                            if (!onlyValid) {
                                Path filePath = resolveManifestFilePath(m);
                                try { Files.deleteIfExists(filePath); } catch (IOException ignore) {}
                                try { Files.deleteIfExists(manifestPath); } catch (IOException ignore) {}
                                System.out.println("[Backup] Altes Backup gelöscht: " + manifestPath);
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (IOException e) {
            System.err.println("[Backup] Fehler bei der Retention-Cleanup: " + e.getMessage());
        }
    }

    /** Prüft, ob das übergeben Backup-Typ das einzige gültige seiner Art ist. */
    private boolean isOnlyValidBackupOfType(String type) throws IOException, ClassNotFoundException {
        Path manifestsDir = backupBaseDir.resolve("manifests");
        if (!Files.exists(manifestsDir)) return true;
        long validCount = 0;
        try (var stream = Files.list(manifestsDir)) {
            for (Path p : stream.toList()) {
                try (var in = Files.newInputStream(p)) {
                    BackupManifest mm = (BackupManifest) new ObjectInputStream(in).readObject();
                    if ("verified".equals(mm.status()) && mm.backupType().equals(type)) {
                        validCount++;
                    }
                }
            }
        }
        return validCount <= 1;
    }
}