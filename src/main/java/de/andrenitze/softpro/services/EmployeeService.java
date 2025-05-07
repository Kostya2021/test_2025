package de.andrenitze.softpro.services;

import org.java_websocket.WebSocket;

import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.players.Player;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service für die Verwaltung von Mitarbeitern.
 */
public interface EmployeeService {
    void dismissEmployee(Player player, Employee employee);
    void simulateEmployeeLives(int tick, ConcurrentHashMap<WebSocket, Player> players);
}