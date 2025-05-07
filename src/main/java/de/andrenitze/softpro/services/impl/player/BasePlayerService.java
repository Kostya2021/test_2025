package de.andrenitze.softpro.services.impl.player;

import de.andrenitze.softpro.domains.players.Player;
import de.andrenitze.softpro.services.PlayerService;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.WebSocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public abstract class BasePlayerService implements PlayerService {
    protected final ConcurrentHashMap<WebSocket, Player> players = new ConcurrentHashMap<>();

    @Override
    public Player getPlayer(WebSocket websocket) {
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
    public WebSocket getWebSocket(Player player) {
        return players.entrySet().stream()
                .filter(entry -> entry.getValue().equals(player))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    @Override
    public ConcurrentHashMap<WebSocket, Player> getPlayers() {
        return players;
    }

    @Override
    public boolean removePlayer(Player player) {
        WebSocket webSocket = getWebSocket(player);
        if (webSocket == null) {
            return false;
        }
        players.remove(webSocket);
        return true;
    }
}