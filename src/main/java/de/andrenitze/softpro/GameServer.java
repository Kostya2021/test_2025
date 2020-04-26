package de.andrenitze.softpro;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GameServer extends WebSocketServer {
    private static final int PLAYERS_NEEDED_FOR_GAME_START = 1;
    private static final String NEW_PLAYER = "NEW_PLAYER";
    private HashSet<Game> games = new HashSet<>();
    private Map<WebSocket, Player> playersAndTheirConnections = new ConcurrentHashMap<>();
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * Creates a {@link #GameServer(String, int)} instance to manage game connections and playersWaitingInLobby.
     *
     * @param hostname String  Host name (IP for clients to connect to)
     * @param port int          Port number (default: 8887)
     */
    GameServer(String hostname, int port) {
        super(new InetSocketAddress(hostname, port));
    }

    void notifyAllClients(String message) {
        broadcast(message);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        logger.debug("Client {} connected", conn.getRemoteSocketAddress());
        playersAndTheirConnections.put(conn, new Player());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        logger.debug("Client {} left the game (exit code {})", conn.getRemoteSocketAddress(), code);

        // Remove disconnected clients from lobby
        playersAndTheirConnections.remove(conn);

        // Remove disconnected clients from all running games
        for (Game game : games) {
            game.removePlayer(conn);
            logger.debug("Client {} left game {} ({} players left)", conn.getRemoteSocketAddress(), game.toString(), game.getPlayers().size());

            // Close the game session if this was the last player
            if (game.getPlayers().size() == 0) {
                logger.debug("Shutting down game {}", game);
                games.remove(game);
                game.shutdown();
                logger.debug("Running games: {}", games.size());
            }
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        logger.debug("received message from {}: {}", conn.getRemoteSocketAddress(), message);

        // Decide which event handler to forward the message to (GameEventHandler, LobbyEventHandler)
        try {
            JSONObject jsonObject = (JSONObject) new JSONParser().parse(message);

            // If it's a new player event, create the player and add her to the lobby
            if (jsonObject.get("eventType").equals(NEW_PLAYER)) {
                JSONObject playerObject = (JSONObject) jsonObject.get("player");
                Player player = new Player(playerObject.get("name").toString(), playerObject.get("company").toString());
                playersAndTheirConnections.put(conn, player);

                logger.debug("New player '{}' added. New number of players in lobby: {}",
                        player.getName(),
                        playersAndTheirConnections.size());
            }
        } catch (ParseException e) {
            logger.error(e.toString());
        }

        // Start a new game session if enough players are waiting in the lobby
        if (playersAndTheirConnections.size() >= PLAYERS_NEEDED_FOR_GAME_START) {
            JSONObject jsonMessage = new JSONObject();
            jsonMessage.put("message", "Enough players connected. Starting game session...");
            broadcast(jsonMessage.toJSONString());

            // Create a new game on this server with all connected playersWaitingInLobby
            games.add( new Game(new ConcurrentHashMap<>(playersAndTheirConnections), this));
            logger.debug("Running games: {}", games.size());

            // Remove all players from the lobby (as everyone waiting should be assigned to a game now)
            playersAndTheirConnections.clear();
        }

        // Dispatch lobby events
        //conn.getResourceDescriptor() // /lobby

        // Dispatch game events
    }

    @Override
    public void onMessage( WebSocket conn, ByteBuffer message ) {
        logger.debug("received ByteBuffer from {}", conn.getRemoteSocketAddress());
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        logger.warn("an error occurred on connection {}; {}", conn.getRemoteSocketAddress(), ex);
    }

    @Override
    public void onStart() {
        logger.info("Server started successfully");
        setConnectionLostTimeout(120);
    }
}