package main.java.de.andrenitze.softpro;

import de.andrenitze.softpro.Game;
import de.andrenitze.softpro.config.GameParameters;
import de.andrenitze.softpro.domains.players.Player;
import de.andrenitze.softpro.domains.projects.Project;
import org.java_websocket.WebSocket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class GameTest {

    private Game game;
    private Player player;
    private Project project;

    @BeforeEach
    void setUp() {
        //game = new Game();
        player = new Player("TestPlayer", "TestCompany");
        project = new Project().initialize();

        WebSocket mockWebSocket = mock(WebSocket.class);
        game.getPlayerService().addPlayer(mockWebSocket, player);

        game.getProjectService().addProject(project);
    }

    @Test
    void testDecisionConsequences() {
        // Simulate a decision made by the player in Level 2
        game.getProjectService().assessProjectRiskForPlayer(project, player,
                game.getLifeCycle().getTick(), game.getLifeCycle().getLevel());

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

        game.getPlayerService().addPlayer(mockWebSocket, testPlayer);

        assertTrue(game.getPlayerService().getPlayers().containsKey(mockWebSocket), "Player should be added to the game");
        assertEquals(testPlayer, game.getPlayerService().getPlayers().get(mockWebSocket), "The correct player should be associated with the WebSocket");
    }
}