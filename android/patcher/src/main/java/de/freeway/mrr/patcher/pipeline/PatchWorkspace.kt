package de.freeway.mrr.patcher.pipeline

import java.io.File

/**
 * Path rules for everything the patcher writes. Only relative POSIX-style
 * paths are ever accepted - anything that could escape the workspace
 * (`..`, backslash, absolute path, empty segment) throws.
 */
object PatchPaths {

    fun isSafe(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        if (path.contains('\\') || path.any { it.code == 0 }) return false
        if (path.startsWith("/")) return false
        return path.split('/').none { it.isEmpty() || it == "." || it == ".." }
    }

    fun requireSafe(path: String?): String {
        require(isSafe(path)) { "Unsafe patch path: $path" }
        return path!!
    }
}

/**
 * The only directory tree the patcher ever writes to:
 *
 * - `incoming/` - files being downloaded (`.part` until complete)
 * - `verified/` - files that passed SHA-256 and wait to be applied
 * - `applied/`  - files in their final position after a patch run
 * - `backup/`   - previous versions of overwritten applied files
 */
class PatchWorkspace(val root: File) {

    val incomingDir: File get() = File(root, "incoming")
    val verifiedDir: File get() = File(root, "verified")
    val appliedDir: File get() = File(root, "applied")
    val backupDir: File get() = File(root, "backup")

    fun ensure(): PatchWorkspace {
        listOf(incomingDir, verifiedDir, appliedDir, backupDir).forEach { dir ->
            require(dir.isDirectory || dir.mkdirs()) { "Cannot create workspace dir: $dir" }
        }
        return this
    }

    fun incomingPartFile(path: String): File =
        File(incomingDir, PatchPaths.requireSafe(path) + ".part")

    fun verifiedFile(path: String): File =
        File(verifiedDir, PatchPaths.requireSafe(path))

    fun appliedFile(path: String): File =
        File(appliedDir, PatchPaths.requireSafe(path))

    fun backupFile(path: String): File =
        File(backupDir, PatchPaths.requireSafe(path) + ".bak")

    /** Removes every partial download; the other directories are untouched. */
    fun clearIncoming() {
        incomingDir.deleteRecursively()
        incomingDir.mkdirs()
    }
}
