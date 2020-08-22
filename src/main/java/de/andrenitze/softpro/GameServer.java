package de.andrenitze.softpro;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GameServer extends WebSocketServer {
    private static final int PLAYERS_NEEDED_FOR_GAME_START = 1;
    private final HashSet<Game> games = new HashSet<>();
    private final Map<WebSocket, Player> playersAndTheirConnections = new ConcurrentHashMap<>();
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * Creates a GameServer instance to manage games and players
     *
     * @param hostname String  Host name (IP for clients to connect to)
     * @param port int          Port number (default: 8887)
     */
    public GameServer(String hostname, int port) {
        super(new InetSocketAddress(hostname, port));
    }

    void notifyAllClients(String message) {
        broadcast(message);
    }

    @Override
    public void onOpen(WebSocket webSocket, ClientHandshake handshake) {
        logger.debug("Client {} connected", webSocket.getRemoteSocketAddress());
        playersAndTheirConnections.put(webSocket, new Player());
        broadcastPlayerList();
    }

    @Override
    public void onClose(WebSocket webSocket, int code, String reason, boolean remote) {
        // Remove disconnected clients from lobby
        playersAndTheirConnections.remove(webSocket);

        // Remove disconnected clients from all running games
        Iterator<Game> iterator = games.iterator();
        while (iterator.hasNext()) {
            Game game = iterator.next();
            game.kickPlayer(webSocket);
            logger.debug("Client left the game ({} players left)", game.getPlayers().size());

            // Close the game session if this was the last player
            if (game.getPlayers().size() == 0) {
                logger.debug("Shutting down game {}", game);
                game.shutdown();
                games.remove(game);
                logger.debug("Running games: {}", games.size());
            }
        }
        broadcastPlayerList();
    }

    @Override
    public void onMessage(WebSocket webSocket, String message) {
        logger.debug("received message from {}: {}", webSocket.getRemoteSocketAddress(), message);

        // Handle lobby events here and forward everything else to the games
        try {
            JSONObject jsonObject = new JSONObject(message);

            // If it's a new player event, create the player and add her to the lobby
            if (jsonObject.get("eventType").equals("NEW_PLAYER")) {
                JSONObject playerObject = (JSONObject) jsonObject.get("player");
                Player player = new Player(playerObject.get("name").toString(), playerObject.get("company").toString());
                addPlayer(webSocket, player);

                logger.debug("New player '{}' added. New number of players in lobby: {}",
                        player.getName(),
                        playersAndTheirConnections.size());
            }

            // Start a new game session if enough players are waiting in the lobby
            if (playersAndTheirConnections.size() >= PLAYERS_NEEDED_FOR_GAME_START) {
                JSONObject jsonMessage = new JSONObject();
                jsonMessage.put("message", "Enough players connected. Starting game session...");
                broadcast(jsonMessage.toString());

                // Create a new game on this server with players from the lobby
                games.add(new Game(new ConcurrentHashMap<>(playersAndTheirConnections), this));
                logger.debug("Running games: {}", games.size());

                // Remove all players from the lobby (as everyone waiting should be assigned to a game now)
                playersAndTheirConnections.clear();
                broadcastPlayerList();
            }

            // Forward all game-related events to the corresponding game instance
            // Find out which game the message belongs to by its' Websocket connection
            // WARNING This is on the critical path, so look for performance issues!
            for (Game game : games) {
                if (game.hasWebSocket(webSocket)) {
                    game.getEventHandler().handleEvent(webSocket, jsonObject);
                }
            }
        } catch (JSONException e) {
            logger.error(e.toString());
        }
    }

    private void broadcastPlayerList() {
        JSONArray playersList = new JSONArray();
        for (Map.Entry<WebSocket, Player> entry : playersAndTheirConnections.entrySet()) {
            Player readyPlayer = entry.getValue();
            JSONObject player = new JSONObject();
            player.put("id", readyPlayer.getId().toString());
            player.put("name", readyPlayer.getName());
            playersList.put(player);
        }
        broadcast("{\"playersInLobby\": " + playersList.toString() + "}");
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteBuffer message) {
        logger.debug("received ByteBuffer from {}", webSocket.getRemoteSocketAddress());
    }

    @Override
    public void onError(WebSocket webSocket, Exception ex) {
        // Most likely a player dropped out of the game and the WebSocket is null
        if (webSocket != null) {
            logger.warn("An error occurred on connection {} : {}", webSocket.getRemoteSocketAddress(), ex.getStackTrace());
        } else {
            logger.warn("An error occured on a connection. {}", ex.getStackTrace());
        }
    }

    @Override
    public void onStart() {
        logger.info("Server started successfully");
    }

    void addPlayer(WebSocket webSocket, Player player) {
        playersAndTheirConnections.put(webSocket, player);
        broadcastPlayerList();
    }
}