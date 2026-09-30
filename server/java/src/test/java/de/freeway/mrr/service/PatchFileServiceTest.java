package de.freeway.mrr.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PatchFileServiceTest {

    @TempDir
    Path patchDir;

    private PatchFileService service() {
        return new PatchFileService(patchDir.toString());
    }

    private Path writeFile(String relative, String content) throws IOException {
        Path target = patchDir.resolve(relative);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
        return target;
    }

    @Test
    void resolvesNestedFileInsideRoot() throws Exception {
        writeFile("packs/demo/core.bin", "hello");
        var found = service().find("packs/demo/core.bin");
        assertTrue(found.isPresent());
        assertEquals("hello", new String(found.get().getContentAsByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    void acceptsAndStripsLeadingSlash() throws Exception {
        writeFile("packs/demo/core.bin", "hello");
        assertTrue(service().find("/packs/demo/core.bin").isPresent());
    }

    @Test
    void rejectsPathTraversalSegments() throws Exception {
        Files.writeString(patchDir.resolveSibling("secret.txt"), "top-secret");
        assertTrue(service().find("../secret.txt").isEmpty());
        assertTrue(service().find("packs/../../secret.txt").isEmpty());
        assertTrue(service().find("a/./../../secret.txt").isEmpty());
    }

    @Test
    void rejectsAbsoluteAndForeignPaths() {
        assertTrue(service().find("").isEmpty());
        assertTrue(service().find("/").isEmpty());
        assertTrue(service().find(null).isEmpty());
        assertTrue(service().find("C:/windows/system32/hosts").isEmpty());
        assertTrue(service().find("..\\secret.txt").isEmpty());
        assertTrue(service().find("packs\\evil.bin").isEmpty());
    }

    @Test
    void rejectsMissingFileAndDirectory() throws Exception {
        assertTrue(service().find("packs/nope.bin").isEmpty());
        Files.createDirectories(patchDir.resolve("packs"));
        assertTrue(service().find("packs").isEmpty());
    }

    @Test
    void rejectsEmptySegmentsButAcceptsDotPrefixedNames() throws Exception {
        assertTrue(service().find("packs//core.bin").isEmpty());
        assertTrue(service().find("./core.bin").isEmpty());
        writeFile(".hidden", "content");
        assertTrue(service().find(".hidden").isPresent());
    }

    @Test
    void safetyCheckIsStrict() {
        assertTrue(PatchFileService.isSafe("a/b/c.bin"));
        assertFalse(PatchFileService.isSafe("a//b"));
        assertFalse(PatchFileService.isSafe("a/../b"));
        assertFalse(PatchFileService.isSafe("../b"));
        assertFalse(PatchFileService.isSafe("./b"));
        assertFalse(PatchFileService.isSafe(""));
        assertFalse(PatchFileService.isSafe(null));
        assertFalse(PatchFileService.isSafe("a\\b"));
        assertFalse(PatchFileService.isSafe("a\0b"));
    }
}
