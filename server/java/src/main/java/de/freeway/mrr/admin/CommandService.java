package de.freeway.mrr.admin;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import de.freeway.mrr.admin.dto.CommandResponseDto;
import de.freeway.mrr.admin.dto.ServerStatusDto;
import de.freeway.mrr.admin.dto.ServerStatsDto;

@Service
public class CommandService {

    private final ServerStatsService statsService;
    private final ServerInfoService infoService;
    private final ActivePlayerTracker activePlayerTracker;
    private final AdminService adminService;

    public CommandService(ServerStatsService statsService,
                         ServerInfoService infoService,
                         ActivePlayerTracker activePlayerTracker,
                         AdminService adminService) {
        this.statsService = statsService;
        this.infoService = infoService;
        this.activePlayerTracker = activePlayerTracker;
        this.adminService = adminService;
    }

    public CommandResponseDto execute(String command) {
        if (command == null || command.isBlank()) {
            return new CommandResponseDto(false, "Empty command", "No command provided", 0);
        }

        String trimmed = command.trim();
        if (!trimmed.startsWith("/")) {
            return new CommandResponseDto(false, "Invalid command", "Commands must start with /", 0);
        }

        long startTime = System.currentTimeMillis();
        String output;
        boolean success;

        try {
            String[] parts = trimmed.substring(1).split("\\s+", 2);
            String cmd = parts[0].toLowerCase();
            String args = parts.length > 1 ? parts[1] : "";

            switch (cmd) {
                case "help" -> {
                    output = buildHelpText();
                    success = true;
                }
                case "status" -> {
                    output = buildStatusText();
                    success = true;
                }
                case "players" -> {
                    output = buildPlayersText();
                    success = true;
                }
                case "uptime" -> {
                    output = "Uptime: " + statsService.getUptimeFormatted();
                    success = true;
                }
                case "version" -> {
                    output = "MRR Server Version: " + infoService.getSpecs().serverVersion();
                    success = true;
                }
                case "backup" -> {
                    if (args.contains("database")) {
                        CommandResponseDto result = adminService.triggerDatabaseBackup();
                        output = result.message();
                        success = result.success();
                    } else if (args.contains("full")) {
                        CommandResponseDto result = adminService.triggerFullBackup();
                        output = result.message();
                        success = result.success();
                    } else {
                        output = "Usage: /backup <database|full>";
                        success = false;
                    }
                }
                default -> {
                    output = "Unknown command: /" + cmd;
                    success = false;
                }
            }
        } catch (Exception e) {
            output = "Error executing command: " + e.getMessage();
            success = false;
        }

        long duration = System.currentTimeMillis() - startTime;
        return new CommandResponseDto(success, output, output, duration);
    }

    private String buildHelpText() {
        return """
                Available commands:
                /help              - Show this help
                /status            - Server status overview
                /players           - List online players
                /uptime            - Server uptime
                /version           - Server version
                /backup database   - Trigger database backup
                /backup full       - Trigger full backup
                """;
    }

    private String buildStatusText() {
        ServerStatusDto status = new ServerStatusDto(
                "online",
                infoService.getSpecs().serverVersion(),
                statsService.getUptimeSeconds(),
                statsService.getUptimeFormatted());
        ServerStatsDto stats = statsService.getStats();
        int activePlayers = activePlayerTracker.getActiveCount();

        return String.format("""
                Server status: ONLINE
                Version: %s
                Uptime: %s
                Players online: %d
                CPU: %.1f%%
                RAM: %s / %s
                """,
                status.version(),
                status.uptimeFormatted(),
                activePlayers,
                stats.cpuUsagePercent(),
                stats.ramUsedFormatted(),
                stats.ramTotalFormatted());
    }

    private String buildPlayersText() {
        Map<String, ActivePlayerTracker.ActivePlayerInfo> players = activePlayerTracker.getActivePlayers();
        if (players.isEmpty()) {
            return "No players online";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Online players: ").append(players.size()).append("\n");
        sb.append(String.format("%-12s %-20s %-20s%n", "Player ID", "Username", "Last Activity"));

        for (ActivePlayerTracker.ActivePlayerInfo info : players.values()) {
            sb.append(String.format("%-12s %-20s %-20s%n",
                    info.playerIdString(),
                    info.username(),
                    formatTimeAgo(info.lastActivity())));
        }
        return sb.toString();
    }

    private String formatTimeAgo(Instant instant) {
        long seconds = Instant.now().getEpochSecond() - instant.getEpochSecond();
        if (seconds < 60) return seconds + " sec ago";
        if (seconds < 3600) return (seconds / 60) + " min ago";
        return (seconds / 3600) + " hours ago";
    }
}
