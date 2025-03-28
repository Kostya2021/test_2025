package de.andrenitze.softpro.services.impl.player;

import de.andrenitze.softpro.Player;
import de.andrenitze.softpro.TalentMarket;
import lombok.Getter;
import org.java_websocket.WebSocket;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static de.andrenitze.softpro.GameServer.RANDOM;

@Service
public class GamePlayerServiceImpl extends BasePlayerService {
    @Getter private final ConcurrentHashMap<WebSocket, Player> players;
    @Getter private final ConcurrentHashMap<WebSocket, Player> lobby;
    private final TalentMarket talentMarket;

    public GamePlayerServiceImpl(TalentMarket talentMarket) {
        this.players = new ConcurrentHashMap<>();
        this.lobby = new ConcurrentHashMap<>();
        this.talentMarket = talentMarket;
    }

    @Override
    public void addPlayer(WebSocket key, Player value) {
        players.put(key, value);
    }

    @Override
    public Player getPlayer(WebSocket websocket) {
        return players.get(websocket);
    }

    @Override
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

    @Override
    public Player removePlayer(WebSocket webSocket) {
        return players.remove(webSocket);
    }

    public boolean isPlayerInAnyGame(Player player) {
        return players.containsValue(player);
    }

    public Player getRandomPlayer() {
        List<Player> playerList = new ArrayList<>(players.values());
        if (playerList.isEmpty()) {
            return null;
        }
        return playerList.get(RANDOM.nextInt(playerList.size()));
    }
}
