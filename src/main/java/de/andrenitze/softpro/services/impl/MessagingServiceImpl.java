package de.andrenitze.softpro.services.impl;

import de.andrenitze.softpro.GameServer;
import de.andrenitze.softpro.Player;
import de.andrenitze.softpro.domains.accounting.AccountingEntry;
import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.projects.Project;
import de.andrenitze.softpro.events.EventType;
import de.andrenitze.softpro.events.GameEvent;
import de.andrenitze.softpro.services.MessagingService;
import de.andrenitze.softpro.services.PlayerService;
import lombok.Setter;
import org.java_websocket.WebSocket;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static de.andrenitze.softpro.GameServer.gson;
import static de.andrenitze.softpro.Main.logger;

@Setter
public class MessagingServiceImpl implements MessagingService {
    private PlayerService playerService;

    @Autowired
    public MessagingServiceImpl(PlayerService playerService) {
        this.playerService = playerService;
    }

    public void sendFundsUpdateToPlayer(Player player) {
        GameEvent<Float> newFundsEvent = new GameEvent<>(EventType.NEW_FUNDS);
        newFundsEvent.setPayload(player.getFunds());
        sendToPlayer(player, gson.toJson(newFundsEvent));
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
            playerService.getPlayers().forEach((_, player) -> sendToPlayer(player, newAccountingEntriesEvent));
        }
    }

    public void broadcast(String message) {
        playerService.getPlayers().forEach((webSocket, _) -> webSocket.send(message));
    }

    public void broadcast(EventType eventType) {
        GameEvent<Void> event = new GameEvent<>(eventType);
        broadcast(gson.toJson(event));
    }

    public void broadcast(GameEvent<?> gameEvent) {
        broadcast(gson.toJson(gameEvent));
    }

    public void broadcastInitialState() {
        playerService.getPlayers().forEach((_, player) -> {
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
            logger.debug("Player {} is not in the game. Not sending event.", player.getId());
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