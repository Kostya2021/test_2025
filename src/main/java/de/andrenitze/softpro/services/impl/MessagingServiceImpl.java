package de.andrenitze.softpro.services.impl;

import de.andrenitze.softpro.GameServer;
import de.andrenitze.softpro.domains.accounting.AccountingEntry;
import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.players.Player;
import de.andrenitze.softpro.domains.projects.Project;
import de.andrenitze.softpro.events.EventType;
import de.andrenitze.softpro.events.GameEvent;
import de.andrenitze.softpro.services.MessagingService;
import de.andrenitze.softpro.services.PlayerService;
import lombok.Setter;
import org.java_websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static de.andrenitze.softpro.GameServer.gson;

@Setter
public class MessagingServiceImpl implements MessagingService {
    private static final Logger log = LoggerFactory.getLogger(MessagingServiceImpl.class);
    private PlayerService playerService;

    @Autowired
    public MessagingServiceImpl(PlayerService playerService) {
        this.playerService = playerService;
    }

    public void sendProjectUpdate(Player player, Project project) {
        GameEvent<Project> projectUpdateEvent = new GameEvent<>(EventType.PROJECT_UPDATED);
        projectUpdateEvent.setPayload(project);
        sendToPlayer(player, gson.toJson(projectUpdateEvent));
    }

    public void sendEmployeeUpdate(Player player, Employee employee) {
        GameEvent<Employee> employeeUpdateEvent = new GameEvent<>(EventType.EMPLOYEE_UPDATED);
        employeeUpdateEvent.setPayload(employee);
        sendToPlayer(player, GameServer.getGson().toJson(employeeUpdateEvent));
    }

    public void sendNewAccountingEntries(List<AccountingEntry> newEntries) {
        if (!newEntries.isEmpty()) {
            GameEvent<List<AccountingEntry>> newAccountingEntriesEvent = new GameEvent<>(EventType.ACCOUNTING_ENTRIES_ADDED);
            newAccountingEntriesEvent.setPayload(newEntries);
            playerService.getPlayers().forEach((ignored, player) -> sendToPlayer(player, newAccountingEntriesEvent));
        }
    }

    public void broadcast(String message) {
        playerService.getPlayers().forEach((webSocket, ignored) -> webSocket.send(message));
    }

    public void broadcast(EventType eventType) {
        GameEvent<Void> event = new GameEvent<>(eventType);
        broadcast(gson.toJson(event));
    }

    public void broadcast(GameEvent<?> gameEvent) {
        broadcast(gson.toJson(gameEvent));
    }

    public void broadcastInitialPlayerState() {
        playerService.getPlayers().forEach((ignored, player) -> {
            GameEvent<Player> event = new GameEvent<>(EventType.STATE_UPDATED);
            event.setPayload(player);
            sendToPlayer(player, event);
        });
    }

    public void sendToPlayer(Player player, GameEvent<?> gameEvent) {
        sendToPlayer(player, gson.toJson(gameEvent));
    }

    public void sendToPlayer(Player player, String message) {
        if (playerService.getPlayers() == null || !playerService.getPlayers().containsValue(player)) {
            log.debug("Player {} is not in the game. Not sending event: {}", player.getId(), message);
            return;
        }

        WebSocket webSocket = getWebSocketByPlayer(playerService.getPlayers(), player);
        if (webSocket != null) {
            webSocket.send(message);
        }
    }

    private WebSocket getWebSocketByPlayer(Map<WebSocket, Player> map, Player player) {
        return map.keySet()
                .stream()
                .filter(key -> player.equals(map.get(key)))
                .findFirst().orElse(null);
    }
}