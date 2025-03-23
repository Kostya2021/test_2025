package de.andrenitze.softpro.services;

import de.andrenitze.softpro.Player;
import de.andrenitze.softpro.domains.employees.Employee;
import org.java_websocket.WebSocket;

/**
 * Service für die Verwaltung von Spielern.
 */
public interface PlayerService {
    void addPlayerToGame(WebSocket key, Player value);
    Player getPlayerByWebSocket(WebSocket websocket);
    boolean hasWebSocket(WebSocket conn);
    void generateFirstEmployeesForPlayers();
    void dismissEmployee(Player player, Employee employee);
    void sendNewObjectives();
}