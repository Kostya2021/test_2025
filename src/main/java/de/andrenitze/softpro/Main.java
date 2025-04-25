package de.andrenitze.softpro;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@Slf4j
public class Main {
    private static final int CONNECTION_LOST_TIMEOUT = 5;

    @Value("${version}")
    private String version;

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(Main.class, args);
        try {
            Main mainApp = context.getBean(Main.class);
            GameServer gameServer = context.getBean(GameServer.class);
            gameServer.setConnectionLostTimeout(CONNECTION_LOST_TIMEOUT);
            log.info("Starting ThatSoftwareGame server version {}", mainApp.version);
            gameServer.run();
        } finally {
            log.info("Shutting down ThatSoftwareGame server");
            SpringApplication.exit(context, () -> 0);
        }
    }
}