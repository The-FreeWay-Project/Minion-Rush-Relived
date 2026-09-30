package de.freeway.mrr.patcher.pipeline

import java.io.File
import java.io.IOException

/**
 * Moves verified files into their final position inside the patch workspace.
 * An already-applied file is preserved as `<path>.bak` in `backup/` before it
 * is overwritten; a target outside the workspace can never be written because
 * every path is validated by [PatchPaths.requireSafe].
 */
class PatchApplier(val workspace: PatchWorkspace) {

    fun ensureWorkspace(): PatchWorkspace = workspace.ensure()

    fun apply(verifiedFile: File, relativePath: String): File {
        PatchPaths.requireSafe(relativePath)
        if (!verifiedFile.isFile) {
            throw IOException("Verified file missing: $relativePath")
        }
        val target = workspace.appliedFile(relativePath)
        target.parentFile?.mkdirs()
        if (target.exists()) {
            val backup = workspace.backupFile(relativePath)
            backup.parentFile?.mkdirs()
            if (backup.exists() && !backup.delete()) {
                throw IOException("Cannot replace backup: $backup")
            }
            if (!target.renameTo(backup)) {
                throw IOException("Cannot back up: $relativePath")
            }
        }
        if (!target.exists() && !verifiedFile.renameTo(target)) {
            throw IOException("Cannot apply: $relativePath")
        }
        return target
    }
}
