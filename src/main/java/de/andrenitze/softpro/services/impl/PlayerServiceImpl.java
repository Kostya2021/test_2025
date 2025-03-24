package de.andrenitze.softpro.services.impl;

import de.andrenitze.softpro.Game;
import de.andrenitze.softpro.Player;
import de.andrenitze.softpro.TalentMarket;
import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.objectives.Objective;
import de.andrenitze.softpro.events.EventType;
import de.andrenitze.softpro.events.GameEvent;
import de.andrenitze.softpro.services.PlayerService;
import lombok.Getter;
import org.java_websocket.WebSocket;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static de.andrenitze.softpro.Main.logger;

@Service
public class PlayerServiceImpl implements PlayerService {
    @Getter
    private final ConcurrentHashMap<WebSocket, Player> players;
    private final Game game;
    private final TalentMarket talentMarket;
    private final ProjectServiceImpl projectService;

    public PlayerServiceImpl(Game game, TalentMarket talentMarket, ProjectServiceImpl projectService) {
        this.game = game;
        this.players = new ConcurrentHashMap<>();
        this.talentMarket = talentMarket;
        this.projectService = projectService;
    }

    public void addPlayer(WebSocket key, Player value) {
        players.put(key, value);
    }

    public Player getPlayerByWebSocket(WebSocket websocket) {
        return players.get(websocket);
    }

    public void removePlayer(WebSocket key) {
        players.remove(key);
    }

    public boolean hasWebSocket(WebSocket conn) {
        return players.containsKey(conn);
    }

    public void generateFirstEmployeesForPlayers() {
        // Remove any existing employees from the player
        players.forEach((_, player) -> player.getEmployees().clear());

        // Generate first employees for all players (necessary for Level 2)
        players.forEach((_, player) -> talentMarket.generateFirstEmployees().forEach(
                employee -> player.addEmployee(employee, 0)
        ));
    }

    public void dismissEmployee(Player player, Employee employee) {
        player.removeEmployee(employee);
        employee.removeAllStatusEffects();
        talentMarket.addTalent(employee);

        // If there are projectService.getProjects()...
        if (projectService.getProjects() != null) {
            projectService.removeEmployeeFromAllProjects(employee);
        }

        // Send employee dismissal confirmation
        GameEvent<Employee> employeeDismissedEvent = new GameEvent<>(EventType.EMPLOYEE_DISMISSED);
        employeeDismissedEvent.setPayload(employee);
        game.getMessagingService().sendEventToPlayer(player, employeeDismissedEvent);

        // Send new employee to all players' TalentMarkets in the game
        GameEvent<ArrayList<Employee>> employeeEvent = new GameEvent<>(EventType.TALENTS_ADDED);
        employeeEvent.setPayload(new ArrayList<>(List.of(employee)));
        game.getMessagingService().broadcastEvent(employeeEvent);
    }

    public void sendNewObjectives() {
        int currentTick = game.getCurrentTick();
        getPlayers().forEach((_, player) -> {
            boolean thereAreNewObjectives = !player.getNewObjectivesByTick(currentTick).isEmpty();
            if (thereAreNewObjectives) {
                logger.debug("Sending {} new objectives to player.", player.getNewObjectivesByTick(currentTick).size());

                // For the frontend, still include ALL objectives, even completed ones, in this event
                GameEvent<List<Objective>> objectivesUpdatedEvent = new GameEvent<>(EventType.OBJECTIVES_UPDATED);
                List<Objective> allObjectives = player.getObjectivesUntilThisTick(currentTick);
                objectivesUpdatedEvent.setPayload(allObjectives);
                game.getMessagingService().sendEventToPlayer(player, objectivesUpdatedEvent);
            }
        });
    }
}
