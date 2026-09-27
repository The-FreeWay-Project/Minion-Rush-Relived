package de.freeway.mrr.util;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * UTC timestamps in ISO-8601 with second precision (e.g.
 * {@code 2026-09-27T12:34:56Z}) — the same wire format the Python MRR
 * server emits, so both backends stay interchangeable.
 */
public final class TimeUtil {

    private TimeUtil() {
    }

    public static String now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
    }

    public static String plusSeconds(long seconds) {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(seconds).toString();
    }

    public static boolean isExpired(String expiresAt) {
        try {
            return !Instant.parse(expiresAt).isAfter(Instant.now());
        } catch (RuntimeException ex) {
            return true;
        }
    }
}
