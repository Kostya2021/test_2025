package de.andrenitze.softpro.config;

import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static de.andrenitze.softpro.Main.logger;

public class Config {
    private static final Properties properties = new Properties();
    private static final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

    static {
        try (InputStream input = Config.class.getClassLoader().getResourceAsStream("project.properties")) {
            if (input != null) {
                properties.load(input);
            } else {
                logger.warn("project.properties not found in classpath");
            }
        } catch (IOException ex) {
            logger.error("Error loading project.properties: {}", ex.getMessage());
        }
    }

    public static String getProperty(String key) {
        String envKey = key.toUpperCase().replace('.', '_');
        String envValue = System.getenv(envKey);

        if (envValue != null) {
            logger.debug("Using System.getenv for key: {}", envKey);
            return envValue;
        }

        envValue = dotenv.get(envKey);
        if (envValue != null) {
            logger.debug("Using dotenv for key: {}", envKey);
            return envValue;
        }

        logger.debug("Using properties file for key: {}", key);
        return properties.getProperty(key);
    }
}
