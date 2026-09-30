package de.freeway.mrr.admin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.freeway.mrr.security.PasswordHasher;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminLoginController {

    private final PasswordHasher passwordHasher;
    private final AdminSessionManager sessionManager;
    private final String dataDir;

    public AdminLoginController(PasswordHasher passwordHasher,
                               AdminSessionManager sessionManager,
                               @Value("${mrr.data-dir:./data}") String dataDir) {
        this.passwordHasher = passwordHasher;
        this.sessionManager = sessionManager;
        this.dataDir = dataDir;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request,
                                  HttpServletResponse response) {
        if (request.username() == null || request.password() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("Username and password required"));
        }

        if (!"admin".equals(request.username())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid credentials"));
        }

        Path adminFile = Paths.get(dataDir, "admin.hash");
        if (!Files.exists(adminFile)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid credentials"));
        }

        try {
            String storedHash = Files.readString(adminFile).trim();
            if (!passwordHasher.verify(storedHash, request.password())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("Invalid credentials"));
            }

            String token = sessionManager.createSession();
            Cookie cookie = new Cookie("MRR_ADMIN_SESSION", token);
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            cookie.setMaxAge(3600);
            response.addCookie(cookie);

            return ResponseEntity.ok(new LoginResponse("success"));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Login failed"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("MRR_ADMIN_SESSION".equals(cookie.getName())) {
                    sessionManager.invalidate(cookie.getValue());
                }
            }
        }
        Cookie clearCookie = new Cookie("MRR_ADMIN_SESSION", "");
        clearCookie.setHttpOnly(true);
        clearCookie.setPath("/");
        clearCookie.setMaxAge(0);
        response.addCookie(clearCookie);
        return ResponseEntity.ok().build();
    }

    public record LoginRequest(String username, String password) {}
    public record LoginResponse(String status) {}
    public record ErrorResponse(String detail) {}
}
