package de.andrenitze.softpro;

import de.andrenitze.softpro.domains.decisions.DecisionDAO;
import de.andrenitze.softpro.domains.employees.TalentMarket;
import de.andrenitze.softpro.domains.players.Player;
import de.andrenitze.softpro.events.GameEventHandler;
import de.andrenitze.softpro.events.GameEventPublisher;
import de.andrenitze.softpro.services.impl.*;
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
                mock(StoryService.class),
                playerService,
                mock(SkillServiceImpl.class),
                mock(AccountingServiceImpl.class),
                messagingService,
                new TalentMarket(),
                mock(ProjectServiceImpl.class),
                mock(EmployeeServiceImpl.class),
                mock(GameEventHandler.class),
                mock(GameEventPublisher.class),
                mock(ProjectEmployeeMappingImpl.class),
                mock(GameLifeCycleService.class),
                mock(LevelConsequencesService.class),
                mock(ObjectiveServiceImpl.class),
                mock(DecisionDAO.class)
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