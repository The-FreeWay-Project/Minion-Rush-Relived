package de.freeway.mrr.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Creates the data directory (property {@code mrr.data-dir}, default
 * {@code ./data}) before any bean — especially the SQLite DataSource — is
 * created. Registered in {@code META-INF/spring.factories}.
 */
public class DataDirectoryEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String dataDir = environment.getProperty("mrr.data-dir", "./data");
        Path directory = Paths.get(dataDir);
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create MRR data directory: " + directory.toAbsolutePath(), ex);
        }
    }

    @Override
    public int getOrder() {
        // After Spring Boot's ConfigData loader has read application.yml.
        return Ordered.LOWEST_PRECEDENCE;
    }
}
