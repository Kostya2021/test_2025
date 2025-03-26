// Java
package de.andrenitze.softpro.services.impl.player;

import de.andrenitze.softpro.Player;
import de.andrenitze.softpro.services.PlayerService;
import org.java_websocket.WebSocket;

import java.util.concurrent.ConcurrentHashMap;

public abstract class BasePlayerService implements PlayerService {
    protected final ConcurrentHashMap<WebSocket, Player> players = new ConcurrentHashMap<>();

    @Override
    public void addPlayer(WebSocket webSocket, Player player) {
        players.put(webSocket, player);
    }

    @Override
    public Player getPlayerByWebSocket(WebSocket websocket) {
        return players.get(websocket);
    }

    @Override
    public boolean hasWebSocket(WebSocket conn) {
        return players.containsKey(conn);
    }

    @Override
    public Player removePlayer(WebSocket webSocket) {
        return players.remove(webSocket);
    }

    @Override
    public ConcurrentHashMap<WebSocket, Player> getPlayers() {
        return players;
    }
}