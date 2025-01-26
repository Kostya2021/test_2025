package main.java.de.andrenitze.softpro;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import de.andrenitze.softpro.*;
import org.java_websocket.WebSocket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GameTest {

    private GameServer gameServer;
    private Game game;
    private Player player;
    private Project project;

    @BeforeEach
    public void setUp() {
        gameServer = new GameServer("TestGameServer", 8070);
        game = new Game(gameServer);
        player = new Player("TestPlayer", "TestCompany");
        project = new Project().initialize();

        WebSocket mockWebSocket = mock(WebSocket.class);
        game.addPlayerToGame(mockWebSocket, player);

        game.addProject(project);
    }

    @Test
    public void testDecisionConsequences() {
        // Simulate a decision made by the player in Level 2
        game.assessProjectRiskForPlayer(project.getId(), player);

        // Move to Level 3
        game.generateFirstEmployeesForPlayers();

        // Check the consequences in Level 3
        assertFalse(player.getEmployees().isEmpty(), "Player should have employees in Level 3");
        assertEquals(-Params.PROJECT_RISK_ASSESSMENT_COST, player.getFunds(), "Player's funds should be deducted correctly");
        assertTrue(game.getProjects().contains(project), "Project should still be part of the game");
    }
}