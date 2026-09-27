package de.freeway.mrr.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.freeway.mrr.exception.NotAuthenticatedException;
import de.freeway.mrr.model.Account;
import de.freeway.mrr.security.AuthFilter;
import de.freeway.mrr.service.AccountService;
import de.freeway.mrr.service.AuthenticationService;

/**
 * Authentication endpoints: register, login, logout. Status codes and payload
 * shapes match the Python MRR server (201/409/422, 200/401, 204/401).
 * Passwords and tokens are never echoed back or logged.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public record RegisterRequest(
            @NotBlank(message = "username must be a non-empty string") @Size(max = 64) String username,
            @NotBlank(message = "password must be a non-empty string") @Size(max = 128) String password) {
    }

    public record AccountResponse(@JsonProperty("account_id") long accountId, String username) {
    }

    public record LoginRequest(@NotNull String username, @NotNull String password) {
    }

    public record LoginResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn) {
    }

    private final AccountService accountService;
    private final AuthenticationService authenticationService;

    public AuthController(AccountService accountService, AuthenticationService authenticationService) {
        this.accountService = accountService;
        this.authenticationService = authenticationService;
    }

    @PostMapping("/register")
    public ResponseEntity<AccountResponse> register(@Valid @RequestBody RegisterRequest request) {
        Account account = accountService.register(request.username(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AccountResponse(account.getId(), account.getUsername()));
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthenticationService.LoginResult result =
                authenticationService.login(request.username(), request.password());
        return new LoginResponse(result.accessToken(), "bearer", result.expiresIn());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String token = (String) request.getAttribute(AuthFilter.ATTR_TOKEN);
        if (token == null) {
            // The filter already rejected every protected route — this only
            // guards against misconfiguration.
            throw new NotAuthenticatedException("Not authenticated");
        }
        authenticationService.logout(token);
        return ResponseEntity.noContent().build();
    }
}
