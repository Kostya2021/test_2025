import de.thbrandenburg.sim.Server.GameServer;

import java.net.InetSocketAddress;

public class Main {
    public static GameServer server;

    public static void main(String[] args) {
        // Create a server that manages clients and game sessions
        String host = "localhost";
        int port = 8887;

        server = new GameServer(new InetSocketAddress(host, port));
        server.run();
        server.start();
   }
}
