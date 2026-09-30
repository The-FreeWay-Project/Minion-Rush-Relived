package de.freeway.mrr.admin;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;

import org.springframework.stereotype.Service;

import de.freeway.mrr.admin.dto.ServerStatsDto;

@Service
public class ServerStatsService {

    private final OperatingSystemMXBean osBean;
    private final RuntimeMXBean runtimeBean;

    public ServerStatsService() {
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.runtimeBean = ManagementFactory.getRuntimeMXBean();
    }

    public ServerStatsDto getStats() {
        Runtime runtime = Runtime.getRuntime();
        long totalRam = runtime.totalMemory();
        long freeRam = runtime.freeMemory();
        long usedRam = totalRam - freeRam;

        double cpuUsage = getCpuUsage();
        int processors = runtime.availableProcessors();
        double loadAvg = osBean.getSystemLoadAverage();

        return new ServerStatsDto(
                cpuUsage,
                usedRam,
                totalRam,
                formatBytes(usedRam),
                formatBytes(totalRam),
                processors,
                loadAvg);
    }

    private double getCpuUsage() {
        try {
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
                return sunOsBean.getCpuLoad() * 100.0;
            }
        } catch (Exception ignored) {
        }
        return osBean.getSystemLoadAverage() * 100.0 / Math.max(1, osBean.getAvailableProcessors());
    }

    public long getUptimeSeconds() {
        return runtimeBean.getUptime() / 1000;
    }

    public String getUptimeFormatted() {
        long uptime = getUptimeSeconds();
        long days = uptime / 86400;
        long hours = (uptime % 86400) / 3600;
        long minutes = (uptime % 3600) / 60;
        long seconds = uptime % 60;
        if (days > 0) {
            return String.format("%dd %dh %dm %ds", days, hours, minutes, seconds);
        }
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        }
        return String.format("%dm %ds", minutes, seconds);
    }

    static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char unit = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), unit);
    }
}
