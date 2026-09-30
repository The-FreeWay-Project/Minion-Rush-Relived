package de.freeway.mrr.admin;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.freeway.mrr.admin.dto.ActivePlayerDto;
import de.freeway.mrr.admin.dto.ActivePlayersDto;
import de.freeway.mrr.admin.dto.BackupInfoDto;
import de.freeway.mrr.admin.dto.BackupStatusDto;
import de.freeway.mrr.admin.dto.CommandRequestDto;
import de.freeway.mrr.admin.dto.CommandResponseDto;
import de.freeway.mrr.admin.dto.NewestPlayerDto;
import de.freeway.mrr.admin.dto.PlayerStatsDto;
import de.freeway.mrr.admin.dto.ServerSpecsDto;
import de.freeway.mrr.admin.dto.ServerStatsDto;
import de.freeway.mrr.admin.dto.ServerStatusDto;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;
    private final ServerStatsService statsService;
    private final ServerInfoService infoService;
    private final PlayerAdminService playerAdminService;
    private final ActivePlayerTracker activePlayerTracker;
    private final CommandService commandService;

    public AdminController(AdminService adminService,
                           ServerStatsService statsService,
                           ServerInfoService infoService,
                           PlayerAdminService playerAdminService,
                           ActivePlayerTracker activePlayerTracker,
                           CommandService commandService) {
        this.adminService = adminService;
        this.statsService = statsService;
        this.infoService = infoService;
        this.playerAdminService = playerAdminService;
        this.activePlayerTracker = activePlayerTracker;
        this.commandService = commandService;
    }

    @GetMapping("/status")
    public ServerStatusDto status() {
        return new ServerStatusDto(
                "online",
                infoService.getSpecs().serverVersion(),
                statsService.getUptimeSeconds(),
                statsService.getUptimeFormatted());
    }

    @GetMapping("/stats")
    public ServerStatsDto stats() {
        return statsService.getStats();
    }

    @GetMapping("/specs")
    public ServerSpecsDto specs() {
        return infoService.getSpecs();
    }

    @GetMapping("/backups")
    public List<BackupInfoDto> backups() {
        return adminService.listBackups();
    }

    @GetMapping("/backups/status")
    public BackupStatusDto backupStatus() {
        return adminService.getBackupStatus();
    }

    @PostMapping("/backups/database")
    public CommandResponseDto databaseBackup() {
        return adminService.triggerDatabaseBackup();
    }

    @PostMapping("/backups/full")
    public CommandResponseDto fullBackup() {
        return adminService.triggerFullBackup();
    }

    @PostMapping("/backups/verify")
    public CommandResponseDto verifyBackups() {
        return adminService.verifyBackups();
    }

    @GetMapping("/players/active")
    public ActivePlayersDto activePlayers() {
        Map<String, ActivePlayerTracker.ActivePlayerInfo> active = activePlayerTracker.getActivePlayers();
        List<ActivePlayerDto> players = new ArrayList<>();
        for (ActivePlayerTracker.ActivePlayerInfo info : active.values()) {
            players.add(new ActivePlayerDto(
                    info.playerId(),
                    info.playerIdString(),
                    info.displayName(),
                    info.username(),
                    formatInstant(info.lastActivity()),
                    formatInstant(info.sessionStarted())));
        }
        return new ActivePlayersDto(players.size(), players);
    }

    @GetMapping("/players/newest")
    public List<NewestPlayerDto> newestPlayers(@RequestParam(defaultValue = "10") int limit) {
        return playerAdminService.getNewestPlayers(limit);
    }

    @GetMapping("/players/stats")
    public PlayerStatsDto playerStats() {
        return playerAdminService.getPlayerStats();
    }

    @PostMapping("/commands")
    public CommandResponseDto executeCommand(@RequestBody CommandRequestDto request) {
        return commandService.execute(request.command());
    }

    private String formatInstant(java.time.Instant instant) {
        return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
                .withZone(ZoneOffset.UTC)
                .format(instant);
    }
}
