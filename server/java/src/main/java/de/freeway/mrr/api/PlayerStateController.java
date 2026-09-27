package de.freeway.mrr.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.freeway.mrr.model.Account;
import de.freeway.mrr.model.Player;
import de.freeway.mrr.model.PlayerState;
import de.freeway.mrr.service.PlayerService;
import de.freeway.mrr.service.PlayerStateService;

/**
 * Protected save-state endpoints for the authenticated account's primary
 * player. {@code player_state} is the single authoritative source for
 * experience, level and coins (MRR v0.3).
 */
@RestController
@RequestMapping("/api/v1/player")
public class PlayerStateController {

    public record PlayerStateRequest(
            @NotNull @Min(0) Integer experience,
            @NotNull @Min(1) Integer level,
            @NotNull @Min(0) Integer coins) {
    }

    public record PlayerStateResponse(
            @JsonProperty("player_id") long playerId,
            int experience,
            int level,
            int coins,
            @JsonProperty("updated_at") String updatedAt) {
    }

    private final PlayerService playerService;
    private final PlayerStateService playerStateService;

    public PlayerStateController(PlayerService playerService, PlayerStateService playerStateService) {
        this.playerService = playerService;
        this.playerStateService = playerStateService;
    }

    @GetMapping("/state")
    public PlayerStateResponse get(HttpServletRequest request) {
        return toResponse(playerStateService.getState(primaryPlayer(request)));
    }

    @PutMapping("/state")
    public PlayerStateResponse put(
            @Valid @RequestBody PlayerStateRequest stateRequest, HttpServletRequest request) {
        PlayerState state = playerStateService.updateState(
                primaryPlayer(request),
                stateRequest.experience(),
                stateRequest.level(),
                stateRequest.coins());
        return toResponse(state);
    }

    private Player primaryPlayer(HttpServletRequest request) {
        Account account = ProfileController.currentAccount(request);
        return playerService.primaryPlayer(account);
    }

    private static PlayerStateResponse toResponse(PlayerState state) {
        return new PlayerStateResponse(
                state.getPlayer().getId(),
                state.getExperience(),
                state.getLevel(),
                state.getCoins(),
                state.getUpdatedAt());
    }
}
