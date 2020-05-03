package de.andrenitze.softpro;

import org.java_websocket.WebSocket;
import org.json.simple.JSONObject;

public class GameEventHandler {
    private final Game game;
    private final GameServer gameServer;

    public GameEventHandler(Game game, GameServer gameServer) {
        this.game = game;
        this.gameServer = gameServer;
    }

    public void handleEvent(WebSocket websocket, JSONObject event) {
        // A player joins a tender
        if (event.get("eventType").equals("JOIN_TENDER")) {
            JSONObject tenderObject = (JSONObject) event.get("tender");

            // Find the corresponding project...
            String projectName = tenderObject.get("name").toString();
            for (Project project : game.getProjects()) {
                if (project.getName().equals(projectName)) {
                    // ...and add the player to the tender process
                    Player player = game.getPlayerByWebSocket(websocket);
                    project.addCompany(player);
                }
            }
        }
    }
}
