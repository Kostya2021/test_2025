package de.andrenitze.softpro.services.impl.player;

import de.andrenitze.softpro.Player;
import de.andrenitze.softpro.TalentMarket;
import org.java_websocket.WebSocket;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.andrenitze.softpro.GameServer.RANDOM;
import static de.andrenitze.softpro.Main.logger;

@Service
public class GamePlayerServiceImpl extends BasePlayerService {
    public static final int MAX_NUMBER_OF_PLAYERS_PER_GAME = 4;
    private final TalentMarket talentMarket;
    private final Map<Player, Integer> playerHashes = new HashMap<>();

    public GamePlayerServiceImpl(TalentMarket talentMarket) {
        this.talentMarket = talentMarket;
    }

    @Override
    public void addPlayer(WebSocket webSocket, Player player) {
        logger.debug("Adding player {}...", player.getId());
        try {
            if (hasWebSocket(webSocket)) {
                logger.warn("Player already exists in the game. Ignoring request to add player.");
                return;
            } else if (getPlayers().size() >= MAX_NUMBER_OF_PLAYERS_PER_GAME) {
                logger.warn("Game is full. Cannot add player.");
                return;
            }

            players.put(webSocket, player);
            logger.debug("Added player {} to game.", player.getId());
        } catch (Exception e) {
            logger.error("Could not add player to game: {}", e.getMessage());
        }
    }

    public void generateFirstEmployeesForPlayers() {
        // Remove any existing employees from the player
        getPlayers().forEach((_, player) -> player.getEmployees().clear());

        // Generate first employees for all players (necessary for Level 2)
        getPlayers().forEach((_, player) -> talentMarket.generateFirstEmployees().forEach(
                player::addEmployee
        ));
    }

    public Player getRandomPlayer() {
        List<Player> playerList = new ArrayList<>(getPlayers().values());
        if (playerList.isEmpty()) {
            return null;
        }
        return playerList.get(RANDOM.nextInt(playerList.size()));
    }

    public Integer getHashForPlayer(Player player) {
        return playerHashes.get(player);
    }

    public void updateHashForPlayer(Player player, int newPlayerHash) {
        playerHashes.put(player, newPlayerHash);
    }
}