package de.freeway.mrr.config;

import java.io.File;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Reads MRR_SQLITE_JDBC_LIB environment variable and sets org.sqlite.lib.path
 * and org.sqlite.lib.name system properties before SQLite JDBC loads its
 * native library. This is required because the SQLite JDBC driver does not
 * read environment variables at runtime — it only checks system properties.
 */
public class SqliteJdbcLibEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String ENV_VAR = "MRR_SQLITE_JDBC_LIB";
    private static final String PROP_PATH = "org.sqlite.lib.path";
    private static final String PROP_NAME = "org.sqlite.lib.name";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String libPath = System.getenv(ENV_VAR);
        if (libPath == null || libPath.trim().isEmpty()) {
            return;
        }

        File libFile = new File(libPath.trim());
        if (!libFile.isFile()) {
            System.err.println("[MRR] SQLite JDBC library not found: " + libPath);
            return;
        }

        String parentDir = libFile.getParent();
        String fileName = libFile.getName();

        if (parentDir != null) {
            System.setProperty(PROP_PATH, parentDir);
        }
        System.setProperty(PROP_NAME, fileName);

        Map<String, Object> props = new HashMap<>();
        props.put(PROP_PATH, parentDir);
        props.put(PROP_NAME, fileName);

        MutablePropertySources sources = environment.getPropertySources();
        sources.addFirst(new MapPropertySource("sqliteJdbcLib", props));
    }
}
