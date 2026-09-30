package de.freeway.mrr.admin.dto;

public record ServerSpecsDto(
        String javaVersion,
        String javaVendor,
        String osName,
        String osVersion,
        String osArch,
        String serverVersion
) {
}
