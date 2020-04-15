package de.thbrandenburg.softpro.Server;

import de.thbrandenburg.softpro.Game;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.simple.JSONObject;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class GameServer extends WebSocketServer {
    private static final int PLAYERS_NEEDED_FOR_GAME_START = 0;
    private Game[] games;
    private HashMap<String, Player> players;
    private final Logger logger = Logger.getLogger(this.getClass().getName());

    /**
     * Creates a {@link #GameServer(String, int)} instance to manage game sessions and players.
     *
     * @param hostname String  Host name (IP for clients to connect to)
     * @param port int          Port number (default: 8887)
     */
    public GameServer(String hostname, int port) {
        super(new InetSocketAddress(hostname, port));
        players = new HashMap<String, Player>();
    }

    public void notifyAllClients(String message) {
        broadcast(message);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        logger.log(Level.INFO, "Connected user with websocket server");

        // Welcome a connected client
        JSONObject message = new JSONObject();
        message.put("message", "Welcome to the server! Waiting for other players to join...");
        conn.send(message.toJSONString());
        message.clear();

        players.put("UNIQUE_CLIENT_ID!!!", new Player());
        logger.log(Level.INFO, "Number of players connected: "+ players.size());

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
        logger.log(Level.INFO, "closed " + conn.getRemoteSocketAddress() + " with exit code " + code + " additional info: " + reason);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        logger.log(Level.INFO, "received message from "	+ conn.getRemoteSocketAddress() + ": " + message);

        // Decide which event handler to forward the message to (GameEventHandler, LobbyEventHandler)

        // Dispatch lobby events
        //conn.getResourceDescriptor() // /lobby

        // Dispatch game events
    }

    @Override
    public void onMessage( WebSocket conn, ByteBuffer message ) {
        logger.log(Level.INFO, "received ByteBuffer from "	+ conn.getRemoteSocketAddress());
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("an error occurred on connection " + conn.getRemoteSocketAddress()  + ":" + ex);
    }

    @Override
    public void onStart() {
        logger.log(Level.INFO, "server started successfully");
        setConnectionLostTimeout(0);
        setConnectionLostTimeout(100);
    }
}