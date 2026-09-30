package de.freeway.mrr.admin;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class AdminSessionManager {

    private final Map<String, Long> sessions = new ConcurrentHashMap<>();
    private final long ttlMillis;

    public AdminSessionManager() {
        this(3600000L);
    }

    public AdminSessionManager(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    public String createSession() {
        String token = generateToken();
        sessions.put(token, System.currentTimeMillis() + ttlMillis);
        return token;
    }

    public boolean isValid(String token) {
        if (token == null) return false;
        Long expiry = sessions.get(token);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            sessions.remove(token);
            return false;
        }
        return true;
    }

    public void invalidate(String token) {
        sessions.remove(token);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
