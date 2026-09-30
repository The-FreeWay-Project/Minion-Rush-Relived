package de.freeway.mrr.api;

/**
 * A single downloadable file entry in a patch manifest.
 *
 * <p>A1.0.0 never ships files ({@code files} is always empty — the patcher
 * only checks versions). The shape exists so later channels can list payloads
 * together with the SHA-256 checksum the client must verify before use; see
 * docs/patcher.md.</p>
 *
 * @param path    relative path inside the MRR patch workspace (and inside the
 *                server's patch directory); never absolute, never {@code ..}
 * @param url     download URL, typically {@code /api/v1/patch/files/<path>}
 * @param sha256  lowercase hex SHA-256 the client must verify before the
 *                downloaded file may be used
 * @param size    expected size in bytes
 */
public record PatchFile(String path, String url, String sha256, long size) {
}
