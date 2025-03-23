package de.andrenitze.softpro.domains.players;

import de.andrenitze.softpro.Game;
import de.andrenitze.softpro.Player;
import de.andrenitze.softpro.ProjectService;
import de.andrenitze.softpro.TalentMarket;
import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.events.EventType;
import de.andrenitze.softpro.events.GameEvent;
import lombok.Getter;
import org.java_websocket.WebSocket;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerService {
    @Getter
    private final ConcurrentHashMap<WebSocket, Player> players;
    private final Game game;
    private final TalentMarket talentMarket;
    private final ProjectService projectService;

    public PlayerService(Game game, TalentMarket talentMarket, ProjectService projectService) {
        this.game = game;
        this.players = new ConcurrentHashMap<>();
        this.talentMarket = talentMarket;
        this.projectService = projectService;
    }

    public void addPlayerToGame(WebSocket key, Player value) {
        players.put(key, value);
    }

    public Player getPlayerByWebSocket(WebSocket websocket) {
        return players.get(websocket);
    }

    public void removePlayerFromGame(WebSocket key) {
        players.remove(key);
        game.closeGameIfEmpty();
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
}
