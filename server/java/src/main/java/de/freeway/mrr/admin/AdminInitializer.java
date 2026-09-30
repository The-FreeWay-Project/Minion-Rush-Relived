package de.freeway.mrr.admin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import de.freeway.mrr.security.PasswordHasher;

@Component
public class AdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final PasswordHasher passwordHasher;
    private final String adminPasswordEnv;

    public AdminInitializer(PasswordHasher passwordHasher,
                           @Value("${mrr.admin.password-env:MRR_ADMIN_PASSWORD}") String adminPasswordEnv) {
        this.passwordHasher = passwordHasher;
        this.adminPasswordEnv = adminPasswordEnv;
    }

    @Override
    public void run(ApplicationArguments args) {
        String password = System.getenv(adminPasswordEnv);
        if (password == null || password.isEmpty()) {
            log.warn("Environment variable {} not set - admin account not initialized", adminPasswordEnv);
            return;
        }

        try {
            Path dataDir = Paths.get(System.getProperty("mrr.data-dir", "./data"));
            Files.createDirectories(dataDir);
            Path adminFile = dataDir.resolve("admin.hash");

            if (Files.exists(adminFile)) {
                log.debug("Admin account already initialized");
                return;
            }

            String hash = passwordHasher.hash(password);
            Files.writeString(adminFile, hash);
            log.info("Admin account initialized successfully");
        } catch (IOException e) {
            log.error("Failed to initialize admin account", e);
        }
    }
}
