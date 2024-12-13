package de.andrenitze.softpro.util;

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
        // Convert key to uppercase and replace '.' with '_'
        String envKey = key.toUpperCase().replace('.', '_');

        // Check environment variables first (OS first, then .env file)
        String envValue = System.getenv(envKey) != null ? System.getenv(envKey) : dotenv.get(envKey);
        if (envValue != null) {
            return envValue;
        }

        // Fallback to properties file
        return properties.getProperty(key);
    }
}
