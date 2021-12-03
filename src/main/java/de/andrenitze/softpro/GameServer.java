package de.andrenitze.softpro;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import de.andrenitze.softpro.events.GameEvent;
import de.andrenitze.softpro.types.EventType;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GameServer extends WebSocketServer {
    private static final int PLAYERS_NEEDED_FOR_GAME_START = 2;
    private final HashSet<Game> games = new HashSet<>();
    private final Map<WebSocket, Player> playersAndTheirConnections = new ConcurrentHashMap<>();
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private static final Gson GSON = new Gson();

    /**
     * Creates a GameServer instance to manage games and players
     *
     * @param hostname String  Host name (IP for clients to connect to)
     * @param port int          Port number (default: 8887)
     */
    public GameServer(String hostname, int port) {
        super(new InetSocketAddress(hostname, port));
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
        for (Game game : games) {
            game.kickPlayer(webSocket);
            logger.debug("A player left the game ({} players are left in the game)", game.getPlayers().size());

            // Close the game session if this was the last player
            if (game.getPlayers().size() == 0) {
                logger.info("Shutting down empty game.");
                game.shutdown();
                games.remove(game);
                logger.info("Running games: {}", games.size());
            }
        }
        broadcastPlayerList();
    }

    @Override
    public void onMessage(WebSocket webSocket, String message) {
        logger.debug("received message from {}: {}", webSocket.getRemoteSocketAddress(), message);

        // Handle lobby events here and forward everything else to the game instances
        try {
            GameEvent<Object> event = GSON.fromJson(message, GameEvent.class);

            // If it's a new player event, create the player and add her to the lobby
            if (event.isOfType(EventType.NEW_PLAYER)) {
                Type payloadType = new TypeToken<GameEvent<Player>>(){}.getType();
                GameEvent<Player> playerEvent = GSON.fromJson(message, payloadType);

                Player player = playerEvent.getPayload();
                addPlayer(webSocket, player);

                logger.debug("New player '{}' added. New number of players in lobby: {}",
                        player.getName(),
                        playersAndTheirConnections.size());
            } else {
                // Forward all other events to the corresponding game instance
                // Find out which game the message belongs to by its Websocket connection
                // WARNING This is on the critical path, so look for performance issues!
                for (Game game : games) {
                    if (game.hasWebSocket(webSocket)) {
                        game.getEventHandler().handleEvent(webSocket, message);
                    }
                }
            }

            // Start a new game session if enough players are waiting in the lobby
            if (playersAndTheirConnections.size() >= PLAYERS_NEEDED_FOR_GAME_START) {
                GameEvent<Object> startEvent = new GameEvent<>();
                startEvent.setEventType(EventType.START_ROUND);
                broadcast(GSON.toJson(startEvent));

                // Create a new game on this server with players from the lobby
                games.add(new Game(new ConcurrentHashMap<>(playersAndTheirConnections), this));
                logger.debug("Running games: {}", games.size());

                // Remove all players from the lobby (as everyone waiting should be assigned to a game now)
                playersAndTheirConnections.clear();
                broadcastPlayerList();
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
        broadcast("{ \"type\": \"UPDATE_LOBBY\", \"payload\": { \"players\": " + playersList + "}}");
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
            logger.warn("An error occurred on a connection. {}", (Object) ex.getStackTrace());
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