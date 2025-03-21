package de.andrenitze.softpro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Properties;

public class Main {
    public static final Logger logger = LoggerFactory.getLogger(Main.class.getName());

    public static void main(String[] args) {
        try {
            String hostAddress = InetAddress.getLocalHost().getHostAddress();

            // Accept first external argument for port or use 80
            int port = args.length > 0 ? Integer.parseInt(args[0]) : 80;

            GameServer server = new GameServer(hostAddress, port);
            server.setConnectionLostTimeout(5);

            final Properties properties = new Properties();
            properties.load(Main.class.getClassLoader().getResourceAsStream("project.properties"));
            String version = properties.getProperty("version");
            logger.info("Starting server version {}", version);

            logger.info("{}:{}", InetAddress.getLocalHost().getHostAddress(), port);
            logger.info(InetAddress.getLocalHost().getHostName());
            server.run();
            server.start();
        } catch (IOException e) {
            logger.warn("Could not load project.properties file. {}", e.toString());
        } catch (Exception e) {
            logger.error(e.toString());
        } finally {
            logger.error("Server stopped.");
        }
   }
}
