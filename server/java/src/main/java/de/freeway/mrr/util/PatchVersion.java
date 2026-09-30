package de.freeway.mrr.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and compares MRR Patcher versions of the form
 * {@code A<major>.<minor>.<patch>} (for example {@code A1.0.0}).
 *
 * <p>The leading {@code A} is optional and case-insensitive so both server and
 * client tolerate {@code A1.0.0} and {@code 1.0.0}. Comparison is numeric per
 * component — {@code A1.10.0} is newer than {@code A1.9.0}. Unparsable input
 * returns {@code null} from {@link #parse(String)} instead of throwing;
 * callers treat {@code null} as "cannot compare".</p>
 */
public final class PatchVersion implements Comparable<PatchVersion> {

    private static final Pattern FORMAT =
            Pattern.compile("^A?(\\d+)\\.(\\d+)\\.(\\d+)$", Pattern.CASE_INSENSITIVE);

    private final int major;
    private final int minor;
    private final int patch;

    private PatchVersion(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    /**
     * Parses {@code A<major>.<minor>.<patch>} (leading {@code A} optional).
     *
     * @return the parsed version, or {@code null} if the input is null, blank
     *         or not in the expected shape
     */
    public static PatchVersion parse(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher matcher = FORMAT.matcher(raw.trim());
        if (!matcher.matches()) {
            return null;
        }
        try {
            return new PatchVersion(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)));
        } catch (NumberFormatException e) {
            return null; // component overflow — treat as unparsable
        }
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getPatch() {
        return patch;
    }

    @Override
    public int compareTo(PatchVersion other) {
        int cmp = Integer.compare(major, other.major);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(minor, other.minor);
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(patch, other.patch);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PatchVersion other)) {
            return false;
        }
        return major == other.major && minor == other.minor && patch == other.patch;
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(major);
        result = 31 * result + Integer.hashCode(minor);
        result = 31 * result + Integer.hashCode(patch);
        return result;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "A%d.%d.%d", major, minor, patch);
    }
}
