package de.andrenitze.softpro;

import org.json.simple.JSONObject;

public class GameEventHandler {
    private Player player;

    public void handleEvent(String newPlayerEvent, JSONObject playerObject) {
        // Create new player from parsed JSON
        JSONObject playerJSONObject = (JSONObject) playerObject.get("player");
        Player player = new Player(playerJSONObject.get("name").toString(), playerJSONObject.get("company").toString());

        // Add player to game

    }
}
