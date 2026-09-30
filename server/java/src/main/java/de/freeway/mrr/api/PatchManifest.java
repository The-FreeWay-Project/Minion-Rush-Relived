package de.freeway.mrr.api;

import java.util.List;

/**
 * DTO for {@code GET /api/v1/patch/manifest}: what the MRR Patcher needs to
 * decide whether an update is available.
 *
 * @param version       latest patcher version on this channel, e.g. {@code A1.0.0}
 * @param channel       release channel, e.g. {@code stable}
 * @param platform      target platform, e.g. {@code android}
 * @param serverVersion version of the mrr-server serving this manifest (e.g. {@code 0.3.0})
 * @param message       human-readable result of the installed-vs-latest comparison
 * @param files         downloadable payloads; empty in A1.0.0, later entries
 *                      carry a {@code sha256} for client-side verification
 */
public record PatchManifest(
        String version,
        String channel,
        String platform,
        String serverVersion,
        String message,
        List<PatchFile> files) {
}
