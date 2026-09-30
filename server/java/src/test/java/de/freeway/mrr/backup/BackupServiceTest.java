package de.freeway.mrr.backup;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

class BackupServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void backupStorageCreatesDirectories() {
        BackupStorage storage = new BackupStorage(tempDir.toString());
        assertTrue(Files.isDirectory(storage.getDatabaseDir()));
        assertTrue(Files.isDirectory(storage.getFullDir()));
        assertTrue(Files.isDirectory(storage.getManifestsDir()));
    }

    @Test
    void databaseBackupServiceCanCreateBackupPath() {
        DatabaseBackupService dbSvc = new DatabaseBackupService(
                Paths.get("").toAbsolutePath().resolve("test-db.sqlite"),
                Paths.get(tempDir.toString(), "database")
        );
        assertDoesNotThrow(() -> dbSvc.createBackup());
    }

    @Test
    void backupManifestCanBeCreatedAndRead() throws Exception {
        BackupManifest manifest = new BackupManifest("database", "test-file.sqlite.gz", 1234, "abc123");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(manifest);
        }
        byte[] data = bos.toByteArray();
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
            BackupManifest readBack = (BackupManifest) ois.readObject();
            assertEquals(manifest.backupType(), readBack.backupType());
            assertEquals(manifest.file(), readBack.file());
            assertEquals(manifest.sha256(), readBack.sha256());
        }
    }

    @Test
    void backupStorageFileOperations() throws IOException {
        BackupStorage storage = new BackupStorage(tempDir.toString());
        Path testFile = storage.writeFile("test.txt", new ByteArrayInputStream("Hello World".getBytes()));
        assertTrue(Files.exists(testFile));
        assertEquals("Hello World", Files.readString(testFile));
    }
}
