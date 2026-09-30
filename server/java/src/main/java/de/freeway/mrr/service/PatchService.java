package de.freeway.mrr.service;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import de.freeway.mrr.api.PatchManifest;
import de.freeway.mrr.util.PatchVersion;

/**
 * Builds the patch manifest served to the MRR Patcher (A1.0.0+).
 *
 * <p>All patcher-related knowledge lives here: the current patcher version,
 * the release channel and the installed-vs-latest message. The controller
 * stays a thin HTTP shell. The server version comes from Spring Boot's
 * build information (project {@code version} in build.gradle); when
 * build-info.properties is absent (e.g. plain IDE runs) it degrades to
 * {@code "unknown"} instead of failing the context.</p>
 */
@Service
public class PatchService {

    /** Latest patcher version this server advertises. */
    public static final String PATCHER_VERSION = "A1.0.0";

    private static final String CHANNEL = "stable";
    private static final String PLATFORM = "android";
    private static final String UP_TO_DATE = "MRR Patcher is up to date.";

    private final String serverVersion;

    public PatchService(ObjectProvider<BuildProperties> buildProperties) {
        BuildProperties props = buildProperties.getIfAvailable();
        this.serverVersion = props != null ? props.getVersion() : "unknown";
    }

    /**
     * Returns the manifest for the given installed patcher version.
     *
     * @param installedVersion version reported by the client, may be null,
     *                         blank or unparsable — then the default
     *                         up-to-date message is served (the client does
     *                         its own comparison anyway)
     */
    public PatchManifest manifest(String installedVersion) {
        return new PatchManifest(
                PATCHER_VERSION,
                CHANNEL,
                PLATFORM,
                serverVersion,
                messageFor(installedVersion),
                List.of());
    }

    private String messageFor(String installedVersion) {
        PatchVersion installed = PatchVersion.parse(installedVersion);
        if (installed == null) {
            return UP_TO_DATE;
        }
        int cmp = installed.compareTo(PatchVersion.parse(PATCHER_VERSION));
        if (cmp == 0) {
            return UP_TO_DATE;
        }
        if (cmp < 0) {
            return "Update to " + PATCHER_VERSION + " available.";
        }
        return "Installed patcher version is ahead of the " + CHANNEL + " channel.";
    }
}
