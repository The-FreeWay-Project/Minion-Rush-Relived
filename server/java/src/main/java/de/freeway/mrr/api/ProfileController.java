package de.freeway.mrr.api;

import jakarta.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import de.freeway.mrr.exception.NotAuthenticatedException;
import de.freeway.mrr.model.Account;
import de.freeway.mrr.model.Player;
import de.freeway.mrr.model.PlayerState;
import de.freeway.mrr.security.AuthFilter;
import de.freeway.mrr.service.PlayerService;
import de.freeway.mrr.service.PlayerStateService;

/**
 * Protected profile endpoint: the authenticated account plus its primary
 * player. Level and coins are composed from {@code player_state} — the single
 * authoritative save-state source (MRR v0.3).
 */
@RestController
public class ProfileController {

    public record ProfilePlayerResponse(
            @JsonProperty("player_id") String playerId,
            @JsonProperty("display_name") String displayName,
            int level,
            int coins) {
    }

    public record ProfileResponse(
            @JsonProperty("account_id") long accountId,
            String username,
            ProfilePlayerResponse player) {
    }

    private final PlayerService playerService;
    private final PlayerStateService playerStateService;

    public ProfileController(PlayerService playerService, PlayerStateService playerStateService) {
        this.playerService = playerService;
        this.playerStateService = playerStateService;
    }

    @GetMapping("/api/v1/profile")
    public ProfileResponse profile(HttpServletRequest request) {
        Account account = currentAccount(request);
        Player player = playerService.primaryPlayer(account);
        ProfilePlayerResponse playerResponse = null;
        if (player != null) {
            PlayerState state = playerStateService.getOrCreate(player);
            playerResponse = new ProfilePlayerResponse(
                    player.getPlayerId(), player.getDisplayName(), state.getLevel(), state.getCoins());
        }
        return new ProfileResponse(account.getId(), account.getUsername(), playerResponse);
    }

    static Account currentAccount(HttpServletRequest request) {
        Account account = (Account) request.getAttribute(AuthFilter.ATTR_ACCOUNT);
        if (account == null) {
            throw new NotAuthenticatedException("Not authenticated");
        }
        return account;
    }
}
