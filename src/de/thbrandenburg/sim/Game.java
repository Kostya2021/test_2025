package de.thbrandenburg.sim;

import org.java_websocket.server.WebSocketServer;
import java.net.InetSocketAddress;

public class Game {
    public void start() {
        String host = "localhost";
        int port = 8887;

        WebSocketServer server = new SimpleServer(new InetSocketAddress(host, port));
        server.run();
        server.start();
    }
}
