package main.java.de.andrenitze.softpro;

import de.andrenitze.softpro.GameServer;
import org.java_websocket.server.WebSocketServer;
import org.junit.After;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import static org.mockito.Mockito.*;

class GameServerTest {
    @Mock
    private WebSocketServer server;

    ArgumentCaptor<Runnable> runnables = ArgumentCaptor.forClass(Runnable.class);

    @BeforeEach
    void setUp() {
        server = mock(GameServer.class);
    }

    @Test
    void testServerStart() {
        server.run();
        server.start();
        verify(server).start();
    }

    @After
    public void validate() {
        validateMockitoUsage();
    }
}
