package de.andrenitze.softpro.config;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Slf4j
public class Config {
    private static final Properties properties = new Properties();
    private static final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

    private Config() {
    }

    static {
        try (InputStream input = Config.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input != null) {
                properties.load(input);
            } else {
                log.warn("application.properties not found in classpath");
            }
        } catch (IOException ex) {
            log.error("Error loading application.properties: {}", ex.getMessage());
        }
    }

    public static String getProperty(String key) {
        String envKey = key.toUpperCase().replace('.', '_');
        String envValue = System.getenv(envKey);

        if (envValue != null) {
            log.debug("Using System.getenv for key: {}", envKey);
            return envValue;
        }

        envValue = dotenv.get(envKey);
        if (envValue != null) {
            log.debug("Using dotenv for key: {}", envKey);
            return envValue;
        }

        log.debug("Using properties file for key: {}", key);
        return properties.getProperty(key);
    }
}
