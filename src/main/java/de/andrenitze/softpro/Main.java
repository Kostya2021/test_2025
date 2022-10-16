package de.andrenitze.softpro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class.getName());

    public static void main(String[] args) {
        logger.trace("Starting server...");
        try {
            String hostAddress = InetAddress.getLocalHost().getHostAddress();
            int port = 8070;

            GameServer server = new GameServer(hostAddress, port);
            server.setConnectionLostTimeout(5);
            logger.info(InetAddress.getLocalHost().getHostAddress()+":"+port);
            logger.info(InetAddress.getLocalHost().getHostName());
            server.run();
            server.start();
        } catch (Exception e) {
            logger.error(e.toString());
        } finally {
            logger.trace("Shutting down the server.");
        }
   }
}
