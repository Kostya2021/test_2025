package de.andrenitze.softpro;

import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.projects.Project;
import de.andrenitze.softpro.events.GameEvent;
import de.andrenitze.softpro.events.EventType;
import org.java_websocket.WebSocket;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Map;

import static de.andrenitze.softpro.GameServer.gson;
import static de.andrenitze.softpro.Main.logger;

public class MessagingService implements PropertyChangeListener {
    private Map<WebSocket, Player> players;

    public MessagingService(Game game) {
        this.players = game.getPlayerService().getPlayers();
        game.addPropertyChangeListener(this);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("players".equals(evt.getPropertyName())) {
            this.players = (Map<WebSocket, Player>) evt.getNewValue();
        }
    }

    public void sendFundsUpdateToPlayer(Player player) {
        GameEvent<Float> newFundsEvent = new GameEvent<>(EventType.NEW_FUNDS);
        newFundsEvent.setPayload(player.getFunds());
        sendMessageToPlayer(player, gson.toJson(newFundsEvent));
    }

    void sendProjectUpdateToPlayer(Player player, Project project) {
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
        // Send the message to all players
        players.forEach((webSocket, _) -> webSocket.send(message));
    }

    public void sendEmployeeUpdate(Player player, Employee employee) {
        GameEvent<Employee> employeeUpdateEvent = new GameEvent<>(EventType.EMPLOYEE_UPDATED);
        employeeUpdateEvent.setPayload(employee);
        sendMessageToPlayer(player, GameServer.getGson().toJson(employeeUpdateEvent));
    }

    public void sendInitialStateToAllPlayers() {
        players.forEach((_, player) -> {
            logger.debug("Sending initial state to players");
            GameEvent<Player> initialPlayerEvent = new GameEvent<>(EventType.STATE_UPDATED);
            initialPlayerEvent.setPayload(player);
            sendMessageToPlayer(player, GameServer.getGson().toJson(initialPlayerEvent));
        });
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
}
