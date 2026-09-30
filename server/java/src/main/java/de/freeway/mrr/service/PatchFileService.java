package de.freeway.mrr.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

/**
 * Serves files from the explicit MRR patch directory only.
 *
 * <p>Every requested path is validated before it touches the file system:
 * no blank paths, no backslashes, no NUL, no {@code .}/{@code ..} segments,
 * and the normalized result must still live under the configured root — so
 * no arbitrary file path outside the patch directory can ever be read.
 * Invalid and missing paths both result in {@link Optional#empty()} and are
 * answered with {@code 404} (existence of outside paths is not revealed).</p>
 */
@Service
public class PatchFileService {

    private final Path root;

    public PatchFileService(@Value("${mrr.patch-dir}") String patchDir) {
        this.root = Path.of(patchDir).toAbsolutePath().normalize();
    }

    /** Absolute patch directory this service is bound to. */
    public Path getRoot() {
        return root;
    }

    /**
     * Resolves {@code relativePath} against the patch directory.
     *
     * @return the file resource, or empty when the path is unsafe, missing
     *         or not a regular file
     */
    public Optional<FileSystemResource> find(String relativePath) {
        String cleaned = stripLeadingSlash(relativePath);
        if (!isSafe(cleaned)) {
            return Optional.empty();
        }
        final Path resolved;
        try {
            resolved = root.resolve(cleaned).normalize();
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
        if (!resolved.startsWith(root) || !Files.isRegularFile(resolved)) {
            return Optional.empty();
        }
        return Optional.of(new FileSystemResource(resolved));
    }

    private static String stripLeadingSlash(String path) {
        if (path == null) {
            return "";
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }

    /** Rejects anything that could escape the patch directory. */
    static boolean isSafe(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        if (path.indexOf('\0') >= 0 || path.indexOf('\\') >= 0) {
            return false;
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        return true;
    }
}
