package de.freeway.mrr.admin;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.springframework.stereotype.Service;

import de.freeway.mrr.admin.dto.ServerSpecsDto;

@Service
public class ServerInfoService {

    public ServerSpecsDto getSpecs() {
        String serverVersion = readServerVersion();
        return new ServerSpecsDto(
                System.getProperty("java.version"),
                System.getProperty("java.vendor"),
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"),
                serverVersion);
    }

    private String readServerVersion() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("META-INF/build-info.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                String version = props.getProperty("build.version");
                if (version != null && !version.isEmpty()) {
                    return version;
                }
            }
        } catch (IOException ignored) {
        }
        return "0.3.0";
    }
}
