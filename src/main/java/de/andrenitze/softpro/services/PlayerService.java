package de.andrenitze.softpro.services;

import de.andrenitze.softpro.Player;
import de.andrenitze.softpro.domains.employees.Employee;
import org.java_websocket.WebSocket;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing players in the lobby or in games.
 */
public interface PlayerService {
    // Common methods for game and lobby
    void addPlayer(WebSocket key, Player value);
    Player getPlayerByWebSocket(WebSocket websocket);
    boolean hasWebSocket(WebSocket conn);
    ConcurrentHashMap<WebSocket, Player> getPlayers();
    Player removePlayer(WebSocket webSocket);

    // Game-specific methods
    void dismissEmployee(Player player, Employee employee);
    void generateFirstEmployeesForPlayers();
}