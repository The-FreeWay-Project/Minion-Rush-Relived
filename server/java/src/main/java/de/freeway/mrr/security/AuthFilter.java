package de.freeway.mrr.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.ObjectMapper;

import de.freeway.mrr.exception.ErrorResponse;
import de.freeway.mrr.model.Account;
import de.freeway.mrr.service.AuthenticationService;

/**
 * Bearer-token authentication for the MRR API.
 *
 * <p>Protected routes (profile, players, player state, logout) require a valid
 * {@code Authorization: Bearer <token>} header; the resolved account and raw
 * token are exposed as request attributes. Public routes (health, register,
 * login) skip token checks entirely, mirroring the Python server.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthFilter extends OncePerRequestFilter {

    public static final String ATTR_ACCOUNT = "mrr.account";
    public static final String ATTR_TOKEN = "mrr.token";

    private static final String NOT_AUTHENTICATED = "Not authenticated";
    private static final String INVALID_TOKEN = "Invalid or expired session token";

    private final AuthenticationService authenticationService;
    private final ObjectMapper objectMapper;

    public AuthFilter(AuthenticationService authenticationService, ObjectMapper objectMapper) {
        this.authenticationService = authenticationService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String path = normalize(request.getRequestURI());
        if (!path.startsWith("/api/v1/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractBearerToken(request.getHeader("Authorization"));
        boolean requiresAuth = requiresAuthentication(path);

        Account account = null;
        if (requiresAuth) {
            if (token == null) {
                writeUnauthorized(response, NOT_AUTHENTICATED);
                return;
            }
            account = authenticationService.authenticateOrNull(token);
            if (account == null) {
                writeUnauthorized(response, INVALID_TOKEN);
                return;
            }
            request.setAttribute(ATTR_ACCOUNT, account);
            request.setAttribute(ATTR_TOKEN, token);
        }

        filterChain.doFilter(request, response);
    }

    static boolean requiresAuthentication(String path) {
        String normalized = normalize(path);
        return normalized.equals("/api/v1/profile")
                || normalized.equals("/api/v1/auth/logout")
                || normalized.startsWith("/api/v1/player");
    }

    static String extractBearerToken(String header) {
        if (header == null || header.length() < 8 || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String value = header.substring(7).trim();
        return value.isEmpty() ? null : value;
    }

    private static String normalize(String path) {
        if (path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }

    private void writeUnauthorized(HttpServletResponse response, String detail) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(new ErrorResponse(detail)));
    }
}
