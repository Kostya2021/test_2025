package de.andrenitze.softpro.services;

import de.andrenitze.softpro.Player;
import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.projects.Project;
import de.andrenitze.softpro.events.EventType;
import de.andrenitze.softpro.events.GameEvent;

/**
 * Service for communication with players over WebSocket.
 */
public interface MessagingService {
    void sendMessageToPlayer(Player player, String message);
    void broadcast(String message);
    void broadcast(GameEvent<?> gameEvent);
    void sendFundsUpdateToPlayer(Player player);
    void sendProjectUpdateToPlayer(Player player, Project project);
    void sendEmployeeUpdate(Player player, Employee employee);
    void broadcast(EventType eventType);
    void sendToPlayer(Player player, GameEvent<?> gameEvent);
    void broadcastInitialState();
}