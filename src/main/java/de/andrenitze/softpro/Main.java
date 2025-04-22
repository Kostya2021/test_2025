package de.andrenitze.softpro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.util.Properties;

@SpringBootApplication
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class.getName());
    private static final int CONNECTION_LOST_TIMEOUT = 5;

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(Main.class, args);
        try {
            GameServer gameServer = context.getBean(GameServer.class);
            gameServer.setConnectionLostTimeout(5);
            String version = loadVersion();
            log.info("Starting ThatSoftwareGame server version {}", version);
            gameServer.run();
        } catch (IOException e) {
            log.error("Error loading version: {}", e.getMessage());
        } finally {
            log.info("Shutting down ThatSoftwareGame server");
            SpringApplication.exit(context, () -> 0);
        }
    }

    private static String loadVersion() throws IOException {
        Properties properties = new Properties();
        properties.load(Main.class.getClassLoader().getResourceAsStream("application.properties"));
        return properties.getProperty("version");
    }
}