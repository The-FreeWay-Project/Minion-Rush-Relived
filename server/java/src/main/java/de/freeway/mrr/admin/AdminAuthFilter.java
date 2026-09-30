package de.freeway.mrr.admin;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

public class AdminAuthFilter extends OncePerRequestFilter {

    private static final String SESSION_COOKIE = "MRR_ADMIN_SESSION";
    private static final Set<String> PROTECTED_PREFIXES = Set.of("/admin", "/api/v1/admin");

    private final AdminSessionManager sessionManager;

    public AdminAuthFilter(AdminSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (isProtected(path)) {
            String token = extractSessionToken(request);
            if (token == null || !sessionManager.isValid(token)) {
                if (path.startsWith("/api/")) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"detail\":\"Not authenticated\"}");
                    return;
                }
                response.sendRedirect("/admin/login");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isProtected(String path) {
        if (path.startsWith("/admin/login") || path.startsWith("/api/v1/admin/login")) {
            return false;
        }
        return PROTECTED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private String extractSessionToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (SESSION_COOKIE.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
