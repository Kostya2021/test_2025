package de.andrenitze.softpro.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static de.andrenitze.softpro.Main.logger;

public class Config {
    private static final Properties properties = new Properties();

    static {
        try (InputStream input = Config.class.getClassLoader().getResourceAsStream("project.properties")) {
            if (input == null) {
                throw new IOException("Cannot find 'project.properties' file in the classpath");
            }
            properties.load(input);
        } catch (IOException ex) {
            logger.error("Error while loading properties file: {}", ex.getMessage());
        }
    }

    public static String getProperty(String key) {
        String envValue = System.getenv(key.toUpperCase().replace('.', '_'));
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }
        return properties.getProperty(key);
    }
}
