package de.freeway.mrr.patcher.pipeline

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

import de.freeway.mrr.patcher.api.PatchManifest
import de.freeway.mrr.patcher.data.PatchApiException
import de.freeway.mrr.patcher.data.PatchRepository
import de.freeway.mrr.patcher.version.UpdateStatus
import de.freeway.mrr.patcher.version.compareVersions

/** Immutable snapshot of the whole patcher; the UI renders exactly this. */
data class PatchManagerState(
    val state: PatchState = PatchState.IDLE,
    val availableVersion: String? = null,
    val serverVersion: String = "\u2013",
    val serverOnline: Boolean? = null,
    val message: String = "Bereit \u2014 nach Updates suchen.",
    val progress: PatchProgress? = null,
)

/**
 * Drives the full pipeline: check (manifest + local version compare),
 * download (streaming into `incoming/`), verify (SHA-256 against the
 * manifest), apply (into `applied/` with `backup/`), verify installation.
 *
 * Every state change goes through [PatchStateMachine]; an illegal edge
 * throws. Failures end in [PatchState.FAILED] with the exception message and
 * always clear partial downloads.
 */
class PatchManager(
    private val repository: PatchRepository,
    private val installedVersion: String,
    private val baseUrl: String = "",
    private val downloader: PatchDownloader = HttpPatchDownloader(),
    private val verifier: PatchVerifier = PatchVerifier(),
    private val applier: PatchApplier? = null,
) {

    private val _state = MutableStateFlow(PatchManagerState())
    val state: StateFlow<PatchManagerState> = _state.asStateFlow()

    private var manifest: PatchManifest? = null

    /** Fetches the manifest and derives UP_TO_DATE / UPDATE_AVAILABLE / FAILED. */
    suspend fun check() {
        if (_state.value.state == PatchState.CHECKING) {
            return
        }
        transition(PatchState.CHECKING, "Verbindung zum Server \u2026")
        try {
            val received = repository.fetchManifest(installedVersion)
            manifest = received
            val serverVersion = received.serverVersion.ifBlank { "unbekannt" }
            when (compareVersions(installedVersion, received.version)) {
                UpdateStatus.INVALID -> transition(
                    PatchState.FAILED,
                    "Unbekanntes Manifest (Version \"${received.version}\")",
                    serverVersion = serverVersion,
                    serverOnline = true,
                    clearAvailable = true,
                )
                UpdateStatus.UP_TO_DATE -> transition(
                    PatchState.UP_TO_DATE,
                    received.message.ifBlank { "MRR Patcher is up to date." },
                    serverVersion = serverVersion,
                    serverOnline = true,
                    clearAvailable = true,
                )
                UpdateStatus.UPDATE_AVAILABLE -> transition(
                    PatchState.UPDATE_AVAILABLE,
                    received.message,
                    serverVersion = serverVersion,
                    serverOnline = true,
                    availableVersion = received.version,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: PatchApiException) {
            transition(
                PatchState.FAILED,
                e.message ?: "Unbekannter Fehler",
                serverOnline = e !is PatchApiException.Network,
                clearAvailable = true,
            )
        } catch (e: Exception) {
            transition(PatchState.FAILED, e.message ?: "Unbekannter Fehler", clearAvailable = true)
        }
    }

    /**
     * Runs the pipeline for the last checked manifest. From UP_TO_DATE this
     * is a verify-only pass; without a usable state it re-checks first and
     * continues directly if the re-check produced one.
     */
    suspend fun runPatch() {
        var current = _state.value.state
        var currentManifest = manifest
        if (currentManifest == null || current !in setOf(PatchState.UP_TO_DATE, PatchState.UPDATE_AVAILABLE)) {
            check()
            current = _state.value.state
            currentManifest = manifest
            if (currentManifest == null || current !in setOf(PatchState.UP_TO_DATE, PatchState.UPDATE_AVAILABLE)) {
                return
            }
        }

        if (current == PatchState.UP_TO_DATE) {
            transition(PatchState.VERIFYING, "SHA-256-Pr\u00fcfung l\u00e4uft \u2026")
            transition(PatchState.VERIFYING_PATCH, "Installation wird gepr\u00fcft \u2026")
            transition(PatchState.SUCCESS, "Already up to date.")
            return
        }

        val files = currentManifest.files
        if (files.isEmpty()) {
            transition(PatchState.FAILED, "Manifest enth\u00e4lt keine Patch-Dateien", clearAvailable = true)
            return
        }
        val activeApplier = applier
        if (activeApplier == null) {
            transition(PatchState.FAILED, "Patch-Workspace nicht konfiguriert", clearAvailable = true)
            return
        }

        var workspace: PatchWorkspace? = null
        try {
            val ws = activeApplier.ensureWorkspace()
            workspace = ws
            val totalBytes = files.sumOf { it.size.coerceAtLeast(0L) }
            transition(
                PatchState.DOWNLOADING,
                "Patch-Dateien werden geladen \u2026",
                progress = PatchProgress(
                    totalBytesTotal = totalBytes,
                    percent = if (totalBytes > 0) 0 else -1,
                ),
            )

            var completedBytes = 0L
            for (entry in files) {
                val path = PatchPaths.requireSafe(entry.path)
                val part = ws.incomingPartFile(path)
                part.parentFile?.mkdirs()
                downloader.download(resolveUrl(entry.url), part) { done, total ->
                    val fileTotal = if (total > 0) total else entry.size.coerceAtLeast(0L)
                    val overall = completedBytes + done
                    _state.update { s ->
                        s.copy(
                            progress = PatchProgress(
                                currentFile = path,
                                fileBytesDone = done,
                                fileBytesTotal = fileTotal,
                                totalBytesDone = overall,
                                totalBytesTotal = totalBytes,
                                percent = if (totalBytes > 0) {
                                    ((overall * 100) / totalBytes).toInt().coerceIn(0, 100)
                                } else {
                                    -1
                                },
                            ),
                        )
                    }
                }
                completedBytes += part.length()
            }

            transition(PatchState.VERIFYING, "SHA-256-Pr\u00fcfung l\u00e4uft \u2026")
            for (entry in files) {
                val path = PatchPaths.requireSafe(entry.path)
                val part = ws.incomingPartFile(path)
                if (!part.isFile || !verifier.verify(part, entry.sha256)) {
                    ws.clearIncoming()
                    transition(PatchState.FAILED, "SHA-256 mismatch: $path", clearAvailable = true)
                    return
                }
                val verified = ws.verifiedFile(path)
                verified.parentFile?.mkdirs()
                if (verified.exists()) verified.delete()
                if (!part.renameTo(verified)) {
                    ws.clearIncoming()
                    transition(PatchState.FAILED, "Datei konnte nicht abgelegt werden: $path", clearAvailable = true)
                    return
                }
            }
            transition(PatchState.READY_TO_PATCH, "Patch ist bereit.")

            transition(PatchState.PATCHING, "Patch wird angewendet \u2026")
            for (entry in files) {
                val path = PatchPaths.requireSafe(entry.path)
                activeApplier.apply(ws.verifiedFile(path), path)
            }

            transition(PatchState.VERIFYING_PATCH, "Installation wird gepr\u00fcft \u2026")
            for (entry in files) {
                val path = PatchPaths.requireSafe(entry.path)
                val applied = ws.appliedFile(path)
                if (!applied.isFile || !verifier.verify(applied, entry.sha256)) {
                    transition(PatchState.FAILED, "Verifikation fehlgeschlagen: $path", clearAvailable = true)
                    return
                }
            }
            transition(PatchState.SUCCESS, "Patch erfolgreich angewendet.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            workspace?.clearIncoming()
            transition(PatchState.FAILED, e.message ?: "Unbekannter Fehler", clearAvailable = true)
        }
    }

    /** Absolute URLs pass through; relative ones join the configured base. */
    private fun resolveUrl(raw: String): String {
        val url = raw.trim()
        if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
            return url
        }
        val base = baseUrl.trim().trimEnd('/')
        return "$base/${url.trimStart('/')}"
    }

    private fun transition(
        to: PatchState,
        message: String,
        serverVersion: String? = null,
        serverOnline: Boolean? = null,
        availableVersion: String? = null,
        clearAvailable: Boolean = false,
        progress: PatchProgress? = null,
    ) {
        val from = _state.value.state
        require(PatchStateMachine.canTransition(from, to)) {
            "Illegal patcher transition $from -> $to"
        }
        _state.update { s ->
            s.copy(
                state = to,
                message = message,
                serverVersion = serverVersion ?: s.serverVersion,
                serverOnline = serverOnline ?: s.serverOnline,
                availableVersion = when {
                    clearAvailable -> null
                    availableVersion != null -> availableVersion
                    else -> s.availableVersion
                },
                progress = progress
                    ?: if (to in terminalStates) null else s.progress,
            )
        }
    }

    private companion object {
        val terminalStates = setOf(
            PatchState.IDLE,
            PatchState.CHECKING,
            PatchState.UP_TO_DATE,
            PatchState.UPDATE_AVAILABLE,
            PatchState.SUCCESS,
            PatchState.FAILED,
        )
    }
}
