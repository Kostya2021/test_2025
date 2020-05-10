package main.java.de.andrenitze.softpro;

import de.andrenitze.softpro.GameServer;
import de.andrenitze.softpro.Main;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.simple.JSONObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class GameServerTest {
    @Mock
    private WebSocketServer server;

    ArgumentCaptor<Runnable> runnables = ArgumentCaptor.forClass(Runnable.class);

    @BeforeEach
    void setUp() {
        server = mock(GameServer.class);
    }

    @Test
    void testServerStart() throws Exception {
        server.run();
        server.start();
        verify(server).start();
    }

    @Test
    void testSingleTenderParticipation() {
        // 1) A game is running with at least one player
        // Send new player
        JSONObject newPlayerEvent = new JSONObject();
        newPlayerEvent.put("eventType", "NEW_PLAYER");
        JSONObject playerObject = new JSONObject();
        playerObject.put("name", "Max");
        playerObject.put("company", "Software GmbH");
        newPlayerEvent.put("eventType", playerObject);

        //client.send(newPlayerEvent.toJSONString());
        // 2) A tender is spawned

        // 3) One player participates in the tender

    }

    @AfterEach
    void tearDown() {

    }
}
