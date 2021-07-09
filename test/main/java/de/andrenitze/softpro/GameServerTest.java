package main.java.de.andrenitze.softpro;

import de.andrenitze.softpro.GameServer;
import org.junit.After;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.validateMockitoUsage;

class GameServerTest {
    @Mock
    private GameServer server;

    @Test
    void testServerStart() {
        server = mock(GameServer.class);
    }

    @After
    public void validate() {
        validateMockitoUsage();
    }
}
