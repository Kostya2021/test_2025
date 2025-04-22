package de.andrenitze.softpro;

import de.andrenitze.softpro.config.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.IOException;
import java.util.Properties;

@SpringBootApplication
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class.getName());
    private static final int CONNECTION_LOST_TIMEOUT = 5;

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(ServerConfig.class)) {

            GameServer gameServer = context.getBean(GameServer.class);
            gameServer.setConnectionLostTimeout(CONNECTION_LOST_TIMEOUT);
            String version = loadVersion();
            log.info("Starting ThatSoftwareGame server version {}", version);
            gameServer.run();
            gameServer.start();
        } catch (IOException e) {
            log.warn("Could not load application.properties file. {}", e.toString());
        } catch (Exception e) {
            log.error(e.toString());
        } finally {
            log.error("Server stopped.");
        }
    }

    private static String loadVersion() throws IOException {
        Properties properties = new Properties();
        properties.load(Main.class.getClassLoader().getResourceAsStream("application.properties"));
        return properties.getProperty("version");
    }
}