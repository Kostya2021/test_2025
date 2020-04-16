import de.andrenitze.softpro.Server.GameServer;

public class Main {
    public static GameServer server;

    public static void main(String[] args) {
        server = new GameServer("localhost", 8887);
        server.run();
        server.start();
   }
}
