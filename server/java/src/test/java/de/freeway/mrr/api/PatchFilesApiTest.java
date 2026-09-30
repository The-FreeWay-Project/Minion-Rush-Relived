package de.freeway.mrr.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PatchFilesApiTest {

    static final Path PATCH_DIR;

    static {
        try {
            PATCH_DIR = Files.createTempDirectory("mrr-patchfiles-test");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void patchDirectory(DynamicPropertyRegistry registry) {
        registry.add("mrr.patch-dir", PATCH_DIR::toString);
    }

    @BeforeAll
    static void createFixtures() throws IOException {
        Files.createDirectories(PATCH_DIR.resolve("packs/demo"));
        Files.writeString(PATCH_DIR.resolve("packs/demo/core.bin"), "hello-patch");
    }

    @AfterAll
    static void cleanUp() throws IOException {
        try (var paths = Files.walk(PATCH_DIR)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesFileFromPatchDirectory() throws Exception {
        mockMvc.perform(get("/api/v1/patch/files/packs/demo/core.bin"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/octet-stream"))
                .andExpect(content().string("hello-patch"))
                .andExpect(content().bytes("hello-patch".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void missingFileReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/patch/files/packs/demo/nope.bin"))
                .andExpect(status().isNotFound());
    }

    @Test
    void directoryReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/patch/files/packs"))
                .andExpect(status().isNotFound());
    }

    @Test
    void pathTraversalIsRejected() throws Exception {
        // The controller answers with an empty-body 404; Spring's own 404
        // handler (when the pattern does not match) returns JSON — both are
        // client errors and neither leaks file content.
        mockMvc.perform(get("/api/v1/patch/files/../secret.txt"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/v1/patch/files/..%2Fsecret.txt"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/v1/patch/files/packs/../../secret.txt"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void fileOutsideThePatchDirectoryIsNeverReadable() throws Exception {
        Path outside = PATCH_DIR.getParent().resolve("outside-secret.txt");
        Files.writeString(outside, "must not leak");
        try {
            mockMvc.perform(get("/api/v1/patch/files/../outside-secret.txt"))
                    .andExpect(status().is4xxClientError());
        } finally {
            Files.deleteIfExists(outside);
        }
    }
}
