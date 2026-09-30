package de.freeway.mrr.patcher.pipeline

/**
 * Lifecycle of the patcher. Every visible transition is validated against
 * [PatchStateMachine]; `ERROR` is gone in favour of `FAILED`, which covers
 * check, download, verification and apply failures alike.
 */
enum class PatchState {
    /** Not checked yet. */
    IDLE,

    /** Manifest request in flight. */
    CHECKING,

    /** Installed version matches or is ahead of the manifest. */
    UP_TO_DATE,

    /** Manifest advertises a newer patcher version with work to do. */
    UPDATE_AVAILABLE,

    /** Streaming the manifest's files into the patch workspace. */
    DOWNLOADING,

    /** SHA-256 of every downloaded file against the manifest. */
    VERIFYING,

    /** All files verified — nothing has been applied yet. */
    READY_TO_PATCH,

    /** Verified files are being moved into their final locations. */
    PATCHING,

    /** Applied files are re-hashed to confirm the result. */
    VERIFYING_PATCH,

    /** Pipeline finished (patch applied or verify-only pass). */
    SUCCESS,

    /** Network/HTTP/decoding failure, bad manifest, unsafe path or bad hash. */
    FAILED,
}

/** Byte progress of the current pipeline run; null outside the pipeline. */
data class PatchProgress(
    val currentFile: String = "",
    val fileBytesDone: Long = 0,
    val fileBytesTotal: Long = 0,
    val totalBytesDone: Long = 0,
    val totalBytesTotal: Long = 0,

    /** 0..100, or -1 when the total size is unknown (indeterminate bar). */
    val percent: Int = -1,
)

/**
 * Allowed state transitions. [PatchManager] refuses any edge that is not
 * listed here, so an illegal sequence fails loudly instead of silently.
 */
object PatchStateMachine {

    private val edges: Map<PatchState, Set<PatchState>> = mapOf(
        PatchState.IDLE to setOf(PatchState.CHECKING),
        PatchState.CHECKING to setOf(
            PatchState.UP_TO_DATE,
            PatchState.UPDATE_AVAILABLE,
            PatchState.FAILED,
        ),
        PatchState.UP_TO_DATE to setOf(
            PatchState.CHECKING,
            PatchState.VERIFYING,
            PatchState.FAILED,
        ),
        PatchState.UPDATE_AVAILABLE to setOf(
            PatchState.CHECKING,
            PatchState.DOWNLOADING,
            PatchState.FAILED,
        ),
        PatchState.DOWNLOADING to setOf(PatchState.VERIFYING, PatchState.FAILED),
        PatchState.VERIFYING to setOf(
            PatchState.READY_TO_PATCH,
            PatchState.VERIFYING_PATCH,
            PatchState.FAILED,
        ),
        PatchState.READY_TO_PATCH to setOf(PatchState.PATCHING, PatchState.FAILED),
        PatchState.PATCHING to setOf(PatchState.VERIFYING_PATCH, PatchState.FAILED),
        PatchState.VERIFYING_PATCH to setOf(PatchState.SUCCESS, PatchState.FAILED),
        PatchState.SUCCESS to setOf(PatchState.CHECKING, PatchState.VERIFYING),
        PatchState.FAILED to setOf(PatchState.CHECKING, PatchState.IDLE),
    )

    fun canTransition(from: PatchState, to: PatchState): Boolean =
        edges[from]?.contains(to) == true
}
