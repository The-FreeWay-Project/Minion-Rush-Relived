package de.freeway.mrr.patcher.api

import kotlinx.serialization.Serializable

/**
 * DTO for `GET /api/v1/patch/manifest` — mirrors the server's
 * `de.freeway.mrr.api.PatchManifest` record.
 *
 * Each `files` entry names the workspace-relative target `path`, the
 * download `url`, the byte `size` (0 = unknown) and the expected `sha256`
 * that is verified client-side before anything is applied.
 */
@Serializable
data class PatchManifest(
    val version: String,
    val channel: String = "",
    val platform: String = "",
    val serverVersion: String = "",
    val message: String = "",
    val files: List<PatchFile> = emptyList(),
)

@Serializable
data class PatchFile(
    val path: String = "",
    val url: String = "",
    val size: Long = 0,
    val sha256: String = "",
)
