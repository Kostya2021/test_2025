package main.java.de.andrenitze.softpro;

import de.andrenitze.softpro.Game;
import de.andrenitze.softpro.domains.employees.TalentMarket;
import de.andrenitze.softpro.domains.players.Player;
import de.andrenitze.softpro.services.impl.MessagingServiceImpl;
import de.andrenitze.softpro.services.impl.player.GamePlayerServiceImpl;
import org.java_websocket.WebSocket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class GameTest {

    private Game game;
    private GamePlayerServiceImpl playerService;
    private MessagingServiceImpl messagingService;

    @BeforeEach
    void setUp() {
        playerService = new GamePlayerServiceImpl(new TalentMarket());
        messagingService = new MessagingServiceImpl(playerService);

        game = new Game(
                mock(de.andrenitze.softpro.services.impl.StoryService.class),
                playerService,
                mock(de.andrenitze.softpro.services.impl.SkillServiceImpl.class),
                mock(de.andrenitze.softpro.services.impl.AccountingServiceImpl.class),
                messagingService,
                new TalentMarket(),
                mock(de.andrenitze.softpro.services.impl.ProjectServiceImpl.class),
                mock(de.andrenitze.softpro.services.impl.EmployeeServiceImpl.class),
                mock(de.andrenitze.softpro.events.GameEventHandler.class),
                mock(de.andrenitze.softpro.events.GameEventPublisher.class),
                mock(de.andrenitze.softpro.services.impl.ProjectEmployeeMappingImpl.class),
                mock(de.andrenitze.softpro.services.impl.GameLifeCycleService.class),
                mock(de.andrenitze.softpro.services.impl.LevelConsequencesService.class),
                mock(de.andrenitze.softpro.services.impl.ObjectiveServiceImpl.class),
                mock(de.andrenitze.softpro.domains.decisions.DecisionDAO.class)
        );
    }

    @Test
    void testAddPlayerToGame() {
        WebSocket mockWebSocket = mock(WebSocket.class);
        Player testPlayer = new Player("TestPlayer", "TestCompany");

        game.getPlayerService().addPlayer(mockWebSocket, testPlayer);

        Map<WebSocket, Player> players = game.getPlayerService().getPlayers();

        assertTrue(players.containsKey(mockWebSocket), "Player should be added to the game");
        assertEquals(testPlayer, players.get(mockWebSocket), "The correct player should be associated with the WebSocket");
    }
}