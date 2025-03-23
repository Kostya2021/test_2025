package de.andrenitze.softpro.domains.players;

import de.andrenitze.softpro.Game;
import de.andrenitze.softpro.Player;
import de.andrenitze.softpro.TalentMarket;
import lombok.Getter;
import org.java_websocket.WebSocket;

import java.util.concurrent.ConcurrentHashMap;

public class PlayerService {
    private final Game game;
    private final TalentMarket talentMarket;
    @Getter
    private final ConcurrentHashMap<WebSocket, Player> players;

    public PlayerService(Game game, TalentMarket talentMarket) {
        this.game = game;
        this.players = new ConcurrentHashMap<>();
        this.talentMarket = talentMarket;
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

    public void generateFirstEmployeesForPlayers() {
        // Remove any existing employees from the player
        players.forEach((_, player) -> player.getEmployees().clear());

        // Generate first employees for all players (necessary for Level 2)
        players.forEach((_, player) -> talentMarket.generateFirstEmployees().forEach(
                employee -> player.addEmployee(employee, 0)
        ));
    }
}
