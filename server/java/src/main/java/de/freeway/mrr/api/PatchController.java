package de.freeway.mrr.api;

import java.io.IOException;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.freeway.mrr.service.PatchFileService;
import de.freeway.mrr.service.PatchService;

/**
 * Update manifest and patch files for the MRR Patcher. Public like
 * {@code /api/v1/health} — the AuthFilter only protects profile/logout/player
 * routes.
 *
 * <p>No logic here: the optional {@code installed} query parameter is passed
 * straight to {@link PatchService}, and file lookups to
 * {@link PatchFileService}, which only ever serves files from the configured
 * MRR patch directory.</p>
 */
@RestController
public class PatchController {

    private final PatchService patchService;
    private final PatchFileService patchFileService;

    public PatchController(PatchService patchService, PatchFileService patchFileService) {
        this.patchService = patchService;
        this.patchFileService = patchFileService;
    }

    @GetMapping("/api/v1/patch/manifest")
    public PatchManifest manifest(
            @RequestParam(name = "installed", required = false) String installed) {
        return patchService.manifest(installed);
    }

    /**
     * Streams a single file out of the configured patch directory. Catch-all
     * path variable supports nested paths ({@code packs/demo/core.bin});
     * unsafe or unknown paths yield {@code 404 Not Found}.
     */
    @GetMapping("/api/v1/patch/files/{*path}")
    public ResponseEntity<FileSystemResource> file(@PathVariable("path") String path) {
        var found = patchFileService.find(path);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        FileSystemResource resource = found.get();
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(resource.contentLength())
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
