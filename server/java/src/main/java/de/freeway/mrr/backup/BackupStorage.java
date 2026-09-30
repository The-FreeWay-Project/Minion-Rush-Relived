package de.freeway.mrr.backup;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public class BackupStorage {

    private final Path baseDir;

    public BackupStorage(String baseDir) {
        this.baseDir = Paths.get(baseDir);
        try {
            Files.createDirectories(this.baseDir.resolve("database"));
            Files.createDirectories(this.baseDir.resolve("full"));
            Files.createDirectories(this.baseDir.resolve("manifests"));
        } catch (IOException e) {
            throw new RuntimeException("Cannot create backup directories", e);
        }
    }

    public Path getDatabaseDir() { return baseDir.resolve("database"); }
    public Path getFullDir() { return baseDir.resolve("full"); }
    public Path getManifestsDir() { return baseDir.resolve("manifests"); }

    /** Erzeugt einen Dateinamen nach Mustern: database/mrr-db-2026-09-28T12-15-00Z.sqlite.gz */
    public String databaseFilename(String type, Instant timestamp) {
        return "database/mrr-db-" + formatTimestamp(timestamp) + ".sqlite.gz";
    }

    public String fullFilename(String type, Instant timestamp) {
        return "full/mrr-full-" + formatTimestamp(timestamp) + ".tar.gz";
    }

    private String formatTimestamp(Instant timestamp) {
        return timestamp.atZone(java.time.ZoneOffset.UTC)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'"));
    }

    /** Speichert ein InputStream als Datei und gibt die zurückgegebene Path zurück. */
    public Path writeFile(String relativePath, java.io.InputStream content) throws IOException {
        Path target = getTargetPath(relativePath);
        try (OutputStream out = Files.newOutputStream(target)) {
            content.transferTo(out);
        }
        return target;
    }

    public Path getTargetPath(String relativePath) {
        String normalized = relativePath.replace("\\", "/");
        String[] parts = normalized.split("/");
        Path current = baseDir;
        for (String part : parts) {
            if (!part.isEmpty()) {
                current = current.resolve(part);
            }
        }
        return current;
    }

    /** Liefert die Path zur Manifest-Datei für einen gegebenen relativen Pfad. */
    public Path manifestPath(String relativePath) {
        String normalized = relativePath.replace("\\", "/");
        // z.B. database/mrr-db-... → manifests/database/mrr-db-...
        String manifestRelative = "manifests/" + normalized;
        return getManifestsDir().resolve(manifestRelative);
    }

    /** Liefert die Relative-Pfad-Form, wie sie im Manifest gespeichert wird. */
    public String manifestRelativePath(String absolutePath) {
        try {
            Path rel = baseDir.relativize(Paths.get(absolutePath));
            return rel.toString().replace("\\", "/");
        } catch (Exception e) {
            return absolutePath;
        }
    }
}