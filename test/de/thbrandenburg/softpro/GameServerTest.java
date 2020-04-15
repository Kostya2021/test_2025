package de.thbrandenburg.softpro;

import de.thbrandenburg.softpro.Server.GameServer;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.simple.JSONObject;
import org.junit.Before;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;

public class GameServerTest {

    static final String WEBSOCKET_URI = "ws://localhost:8887/lobby";
    static final String WEBSOCKET_TOPIC = "/topic";

    WebSocketClient webSocketClient;

    @Before
    public void setup() throws URISyntaxException {
        webSocketClient = new WebSocketClient(new URI(WEBSOCKET_URI)) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {

            }

            @Override
            public void onMessage(String s) {

            }

            @Override
            public void onClose(int i, String s, boolean b) {

            }

            @Override
            public void onError(Exception e) {

            }
        };
    }

    @Test
    public void testServerReadiness() {
        // When the server is running a WebSocket should be accepting
        // connections on a default port (8887) via WebSocket protocol.
        GameServer server = new GameServer("localhost", 8887);

        String url = "ws://127.0.0.1:8887/lobby";
        // Is the server started?

    }

    @Test
    public void testAddingOnePlayer() {
        // Try to connect to the server
        JSONObject connectionRequest = new JSONObject();
        connectionRequest.put("type", "newPlayer");
        connectionRequest.put("clientID", "client-1");

        // Are we greeted by the server?
        //String response = new JSONParser().parse();
        //assertTrue(response["message"] == "Welcome to the server!");

        // Are we added to the list of players?
        //assertTrue();
    }

    @Test
    public void testAddingTwoPlayersAndStartingGame() {

    }
}
