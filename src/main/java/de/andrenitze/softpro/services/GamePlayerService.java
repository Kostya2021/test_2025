package de.andrenitze.softpro.services;

import de.andrenitze.softpro.Player;
import org.java_websocket.WebSocket;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing players in the lobby or in games.
 */
public interface GamePlayerService {
    void addPlayer(WebSocket key, Player value);
    Player getPlayer(WebSocket websocket);
    boolean hasWebSocket(WebSocket conn);
    WebSocket getWebSocket(Player player);
    ConcurrentHashMap<WebSocket, Player> getPlayers();
    boolean removePlayer(Player player);
    Player removePlayer(WebSocket webSocket);
}