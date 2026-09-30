package de.freeway.mrr.patcher.version

/**
 * Parses and compares MRR Patcher versions of the form `A<major>.<minor>.<patch>`
 * (for example `A1.0.0`). The leading `A` is optional and case-insensitive;
 * comparison is numeric per component, so `A1.10.0` is newer than `A1.9.0`.
 * Unparsable input returns null instead of throwing.
 */
data class PatcherVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<PatcherVersion> {

    override fun compareTo(other: PatcherVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    override fun toString(): String = "A$major.$minor.$patch"

    companion object {
        private val FORMAT = Regex("^A?(\\d+)\\.(\\d+)\\.(\\d+)$", RegexOption.IGNORE_CASE)

        fun parse(raw: String?): PatcherVersion? {
            val trimmed = raw?.trim() ?: return null
            val match = FORMAT.matchEntire(trimmed) ?: return null
            val (major, minor, patch) = match.destructured
            return try {
                PatcherVersion(major.toInt(), minor.toInt(), patch.toInt())
            } catch (e: NumberFormatException) {
                null // component overflow — treat as unparsable
            }
        }
    }
}

/** Result of comparing the installed patcher version against a manifest. */
enum class UpdateStatus {
    /** Installed version equals or is ahead of the manifest version. */
    UP_TO_DATE,

    /** Manifest advertises a newer version. */
    UPDATE_AVAILABLE,

    /** Installed or manifest version could not be parsed. */
    INVALID,
}

/**
 * Compares two raw version strings (`installed` vs. `latest`). Null or
 * unparsable input yields [UpdateStatus.INVALID] — the caller decides how
 * to surface that (the UI shows an error instead of guessing).
 */
fun compareVersions(installedRaw: String?, latestRaw: String?): UpdateStatus {
    val installed = PatcherVersion.parse(installedRaw) ?: return UpdateStatus.INVALID
    val latest = PatcherVersion.parse(latestRaw) ?: return UpdateStatus.INVALID
    return if (installed >= latest) UpdateStatus.UP_TO_DATE else UpdateStatus.UPDATE_AVAILABLE
}
