package de.andrenitze.softpro.services.impl;

import de.andrenitze.softpro.GameServer;
import de.andrenitze.softpro.Player;
import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.projects.Project;
import de.andrenitze.softpro.events.EventType;
import de.andrenitze.softpro.events.GameEvent;
import de.andrenitze.softpro.events.PlayersChangedEvent;
import de.andrenitze.softpro.services.MessagingService;
import org.java_websocket.WebSocket;
import org.springframework.context.event.EventListener;

import java.util.HashMap;
import java.util.Map;

import static de.andrenitze.softpro.GameServer.gson;
import static de.andrenitze.softpro.Main.logger;

public class MessagingServiceImpl implements MessagingService {
    private Map<WebSocket, Player> players;

    public MessagingServiceImpl() {
        players = new HashMap<>();
    }

    public void sendFundsUpdateToPlayer(Player player) {
        GameEvent<Float> newFundsEvent = new GameEvent<>(EventType.NEW_FUNDS);
        newFundsEvent.setPayload(player.getFunds());
        sendMessageToPlayer(player, gson.toJson(newFundsEvent));
    }

    public void sendProjectUpdateToPlayer(Player player, Project project) {
        GameEvent<Project> projectUpdateEvent = new GameEvent<>(EventType.PROJECT_UPDATED);
        projectUpdateEvent.setPayload(project);
        sendMessageToPlayer(player, gson.toJson(projectUpdateEvent));
    }

    public void sendMessageToPlayer(Player player, String message) {
        // Get the WebSocket connection of the player
        WebSocket webSocket = getWebSocketByPlayer(players, player);

        if (webSocket != null) {
            // Send a single message on that WebSocket connection
            webSocket.send(message);
        }
    }

    private WebSocket getWebSocketByPlayer(Map<WebSocket, Player> map, Player player) {
        return map.keySet()
                .stream()
                .filter(key -> player.equals(map.get(key)))
                .findFirst().orElse(null);
    }

    public void broadcastToAllPlayers(String message) {
        players.forEach((webSocket, _) -> webSocket.send(message));
    }

    public void sendEmployeeUpdate(Player player, Employee employee) {
        GameEvent<Employee> employeeUpdateEvent = new GameEvent<>(EventType.EMPLOYEE_UPDATED);
        employeeUpdateEvent.setPayload(employee);
        sendMessageToPlayer(player, GameServer.getGson().toJson(employeeUpdateEvent));
    }

    /**
     * Broadcasts an event of specified type (without payload) to all players.
     * @param eventType The type of the event to broadcast.
     */
    public void broadcastEvent(EventType eventType) {
        GameEvent<Void> event = new GameEvent<>(eventType);
        broadcastToAllPlayers(gson.toJson(event));
    }

    /**
     * Broadcasts an event of specified type with the current tick as payload to all players.
     * @param eventType The type of the event to broadcast.
     * @param currentTick The current tick of the game.
     */
    public void broadcastEvent(EventType eventType, int currentTick) {
        GameEvent<Integer> event = new GameEvent<>(eventType);
        event.setPayload(currentTick);
        broadcastToAllPlayers(gson.toJson(event));
    }

    public void broadcastEvent(GameEvent<?> gameEvent) {
        broadcastToAllPlayers(gson.toJson(gameEvent));
    }

    public void sendEventToPlayer(Player player, GameEvent<?> gameEvent) {
        // Check if player is in the game
        if (players == null || !players.containsValue(player)) {
            logger.debug("Player {} is not in the game. Not sending event.", player.getId());
            return;
        }
        sendMessageToPlayer(player, gson.toJson(gameEvent));
    }

    /**
     * Broadcasts the initial state of the game to all players.
     */
    public void broadcastInitialState() {
        players.forEach((_, player) -> {
            GameEvent<Player> event = new GameEvent<>(EventType.STATE_UPDATED);
            event.setPayload(player);
            sendEventToPlayer(player, event);
        });
    }

    @EventListener
    public void onPlayersChanged(PlayersChangedEvent event) {
        logger.debug("PlayersChangedEvent received in MessagingServiceImpl. Adding {} players.", event.getPlayers().size());
        players = event.getPlayers();
    }

    // This is redundant, but the event listener is not working for some reason.
    public void addPlayer(WebSocket key, Player value) {
        logger.debug("Adding player {} to MessagingServiceImpl.", value.getId());
        players.put(key, value);
    }
}
