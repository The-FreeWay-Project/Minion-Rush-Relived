package de.freeway.mrr.api;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.freeway.mrr.model.Account;
import de.freeway.mrr.model.Player;
import de.freeway.mrr.service.PlayerService;

/**
 * Player endpoints (synthetic test players only).
 *
 * <p>MRR v0.3 rule: every player route is authenticated and the owning
 * account is derived server-side from the session — a client can never set or
 * address a foreign {@code account_id}. Players of other accounts behave
 * exactly like missing ones (404).</p>
 */
@RestController
@RequestMapping("/api/v1/players")
public class PlayerController {

    public record PlayerRequest(
            @JsonProperty("player_id")
            @NotBlank(message = "player_id must be a non-empty string") @Size(max = 128) String playerId,
            @JsonProperty("display_name")
            @NotBlank(message = "display_name must be a non-empty string") @Size(max = 128) String displayName) {
    }

    public record PlayerResponse(
            long id,
            @JsonProperty("player_id") String playerId,
            @JsonProperty("display_name") String displayName,
            @JsonProperty("created_at") String createdAt) {
    }

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @PostMapping
    public ResponseEntity<PlayerResponse> create(
            @Valid @RequestBody PlayerRequest request, HttpServletRequest httpRequest) {
        Account account = ProfileController.currentAccount(httpRequest);
        Player player = playerService.createOwned(account, request.playerId(), request.displayName());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(player));
    }

    @GetMapping
    public List<PlayerResponse> list(HttpServletRequest request) {
        Account account = ProfileController.currentAccount(request);
        return playerService.listOwned(account).stream().map(PlayerController::toResponse).toList();
    }

    @GetMapping("/{playerId}")
    public PlayerResponse get(@PathVariable("playerId") String playerId, HttpServletRequest request) {
        Account account = ProfileController.currentAccount(request);
        return toResponse(playerService.getOwned(account, playerId));
    }

    @DeleteMapping("/{playerId}")
    public ResponseEntity<Void> delete(@PathVariable("playerId") String playerId, HttpServletRequest request) {
        Account account = ProfileController.currentAccount(request);
        playerService.deleteOwned(account, playerId);
        return ResponseEntity.noContent().build();
    }

    private static PlayerResponse toResponse(Player player) {
        return new PlayerResponse(player.getId(), player.getPlayerId(), player.getDisplayName(),
                player.getCreatedAt());
    }
}
