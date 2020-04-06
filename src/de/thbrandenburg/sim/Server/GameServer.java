package de.thbrandenburg.sim.Server;

import de.thbrandenburg.sim.Game;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.simple.JSONObject;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public final class GameServer extends WebSocketServer {
    Game game;

    public GameServer(InetSocketAddress address) {
        super(address);
    }

    public void notifyAllClients(String message) {
        broadcast(message);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        // Welcome a connected client
        JSONObject message = new JSONObject();
        message.put("message", "Welcome to the server!");
        conn.send(message.toJSONString());
        message.clear();

        // TODO Check if enough players are "ready" for a game session to start
        // FIXME Currently opens a single game session (thread) for each connecting player ;D
        // Start game session
        message.put("message", "Enough players connected. Starting game session...");
        broadcast(message.toJSONString());
        game = new Game(this);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("closed " + conn.getRemoteSocketAddress() + " with exit code " + code + " additional info: " + reason);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("received message from "	+ conn.getRemoteSocketAddress() + ": " + message);
        // Do something with the client event
    }

    @Override
    public void onMessage( WebSocket conn, ByteBuffer message ) {
        System.out.println("received ByteBuffer from "	+ conn.getRemoteSocketAddress());
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("an error occurred on connection " + conn.getRemoteSocketAddress()  + ":" + ex);
    }

    @Override
    public void onStart() {
        System.out.println("server started successfully");
        setConnectionLostTimeout(0);
        setConnectionLostTimeout(100);
    }
}