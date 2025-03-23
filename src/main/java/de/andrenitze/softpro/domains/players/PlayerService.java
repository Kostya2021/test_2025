package de.andrenitze.softpro.domains.players;

import de.andrenitze.softpro.Game;
import de.andrenitze.softpro.Player;
import lombok.Getter;
import org.java_websocket.WebSocket;

import java.util.concurrent.ConcurrentHashMap;

public class PlayerService {
    private final Game game;
    @Getter
    private final ConcurrentHashMap<WebSocket, Player> players;

    public PlayerService(Game game) {
        this.game = game;
        this.players = new ConcurrentHashMap<>();
    }

    public void addPlayerToGame(WebSocket key, Player value) {
        players.put(key, value);
    }

    public Player getPlayerByWebSocket(WebSocket websocket) {
        return players.get(websocket);
    }

    public void removePlayerFromGame(WebSocket key) {
        players.remove(key);
        game.closeGameIfEmpty();
    }

    public boolean hasWebSocket(WebSocket conn) {
        return players.containsKey(conn);
    }
}
