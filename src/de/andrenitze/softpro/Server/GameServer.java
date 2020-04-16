package de.andrenitze.softpro.Server;

import de.andrenitze.softpro.Game;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;

public final class GameServer extends WebSocketServer {
    private static final int PLAYERS_NEEDED_FOR_GAME_START = 1;
    private Game[] games;
    private final HashMap<String, Player> players;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private JSONObject message;

    /**
     * Creates a {@link #GameServer(String, int)} instance to manage game sessions and players.
     *
     * @param hostname String  Host name (IP for clients to connect to)
     * @param port int          Port number (default: 8887)
     */
    public GameServer(String hostname, int port) {
        super(new InetSocketAddress(hostname, port));
        players = new HashMap<>();
    }

    public void notifyAllClients(String message) {
        broadcast(message);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        logger.info("Connected user with websocket server");

        // Welcome a connected client
        message = new JSONObject();
        message.put("message", "Welcome to the server! Waiting for other players to join...");
        conn.send(message.toJSONString());
        message.clear();

        players.put("player_" + players.size(), new Player());
        logger.info("Number of players connected: {}", players.size());

        // FIXME Currently opens a single game session (thread) for each connecting player ;D
        // Start game session, if enough players are "ready" for a game session to start
        if (players.size() >= PLAYERS_NEEDED_FOR_GAME_START) {
            message.put("message", "Enough players connected. Starting game session...");
            broadcast(message.toJSONString());
            games[0] = new Game(this);
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        logger.info("closed " + conn.getRemoteSocketAddress() + " with exit code " + code + " additional info: " + reason);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        logger.info("received message from "	+ conn.getRemoteSocketAddress() + ": " + message);

        // Decide which event handler to forward the message to (GameEventHandler, LobbyEventHandler)

        // Dispatch lobby events
        //conn.getResourceDescriptor() // /lobby

        // Dispatch game events
    }

    @Override
    public void onMessage( WebSocket conn, ByteBuffer message ) {
        logger.info("received ByteBuffer from "	+ conn.getRemoteSocketAddress());
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        logger.warn("an error occurred on connection " + conn.getRemoteSocketAddress()  + ":" + ex);
    }

    @Override
    public void onStart() {
        logger.info("server started successfully");
        setConnectionLostTimeout(0);
        setConnectionLostTimeout(100);
    }
}