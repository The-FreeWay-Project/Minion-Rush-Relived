package de.freeway.mrr.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import de.freeway.mrr.admin.dto.CommandResponseDto;

class CommandServiceTest {

    private final ServerStatsService statsService = new ServerStatsService();
    private final ServerInfoService infoService = new ServerInfoService();
    private final ActivePlayerTracker tracker = new ActivePlayerTracker();
    private final AdminService adminService = null;

    private CommandService createCommandService() {
        return new CommandService(statsService, infoService, tracker, adminService);
    }

    @Test
    void helpCommandReturnsAvailableCommands() {
        CommandService service = createCommandService();
        CommandResponseDto result = service.execute("/help");

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("/help");
        assertThat(result.output()).contains("/status");
        assertThat(result.output()).contains("/players");
        assertThat(result.output()).contains("/uptime");
        assertThat(result.output()).contains("/version");
        assertThat(result.output()).contains("/backup");
    }

    @Test
    void statusCommandReturnsServerStatus() {
        CommandService service = createCommandService();
        CommandResponseDto result = service.execute("/status");

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("Server status: ONLINE");
        assertThat(result.output()).contains("Players online:");
    }

    @Test
    void playersCommandReturnsOnlinePlayers() {
        CommandService service = createCommandService();
        CommandResponseDto result = service.execute("/players");

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("No players online");
    }

    @Test
    void uptimeCommandReturnsUptime() {
        CommandService service = createCommandService();
        CommandResponseDto result = service.execute("/uptime");

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("Uptime:");
    }

    @Test
    void versionCommandReturnsVersion() {
        CommandService service = createCommandService();
        CommandResponseDto result = service.execute("/version");

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("MRR Server Version:");
    }

    @Test
    void unknownCommandReturnsError() {
        CommandService service = createCommandService();
        CommandResponseDto result = service.execute("/unknown");

        assertThat(result.success()).isFalse();
        assertThat(result.output()).contains("Unknown command: /unknown");
    }

    @Test
    void emptyCommandReturnsError() {
        CommandService service = createCommandService();
        CommandResponseDto result = service.execute("");

        assertThat(result.success()).isFalse();
    }

    @Test
    void nullCommandReturnsError() {
        CommandService service = createCommandService();
        CommandResponseDto result = service.execute(null);

        assertThat(result.success()).isFalse();
    }

    @Test
    void commandWithoutSlashReturnsError() {
        CommandService service = createCommandService();
        CommandResponseDto result = service.execute("help");

        assertThat(result.success()).isFalse();
        assertThat(result.output()).contains("Commands must start with /");
    }

    @Test
    void backupWithoutArgsReturnsUsage() {
        CommandService service = createCommandService();
        CommandResponseDto result = service.execute("/backup");

        assertThat(result.success()).isFalse();
        assertThat(result.output()).contains("Usage: /backup <database|full>");
    }

    @Test
    void commandInjectionAttemptDoesNotExecute() {
        CommandService service = createCommandService();
        CommandResponseDto result = service.execute("/status; rm -rf /");

        assertThat(result.success()).isFalse();
        assertThat(result.output()).contains("Unknown command");
    }

    @Test
    void commandWithPipeDoesNotExecute() {
        CommandService service = createCommandService();
        CommandResponseDto result = service.execute("/status | cat /etc/passwd");

        assertThat(result.success()).isTrue();
        assertThat(result.output()).doesNotContain("root:");
    }
}
