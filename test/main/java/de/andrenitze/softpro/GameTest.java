package main.java.de.andrenitze.softpro;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import de.andrenitze.softpro.*;
import de.andrenitze.softpro.config.GameParameters;
import de.andrenitze.softpro.domains.projects.Project;
import org.java_websocket.WebSocket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameTest {

    private Game game;
    private Player player;
    private Project project;

    @BeforeEach
    void setUp() {
        GameServer gameServer = new GameServer("TestGameServer", 8070);
        game = new Game(gameServer);
        player = new Player("TestPlayer", "TestCompany");
        project = new Project().initialize();

        WebSocket mockWebSocket = mock(WebSocket.class);
        game.addPlayerToGame(mockWebSocket, player);

        game.getProjectService().addProject(project);
    }

    @Test
    void testDecisionConsequences() {
        // Simulate a decision made by the player in Level 2
        game.assessProjectRiskForPlayer(project.getId(), player);

        // Move to Level 3
        game.getPlayerService().generateFirstEmployeesForPlayers();

        // Check the consequences in Level 3
        assertFalse(player.getEmployees().isEmpty(), "Player should have employees in Level 3");
        assertEquals(-GameParameters.PROJECT_RISK_ASSESSMENT_COST, player.getFunds(), "Player's funds should be deducted correctly");
        assertTrue(game.getProjectService().getProjects().contains(project), "Project should still be part of the game");
    }

    @Test
    void testAddPlayerToGame() {
        WebSocket mockWebSocket = mock(WebSocket.class);
        Player testPlayer = new Player("TestPlayer", "TestCompany");

        game.addPlayerToGame(mockWebSocket, testPlayer);

        assertTrue(game.getPlayerService().getPlayers().containsKey(mockWebSocket), "Player should be added to the game");
        assertEquals(testPlayer, game.getPlayerService().getPlayers().get(mockWebSocket), "The correct player should be associated with the WebSocket");
    }
}