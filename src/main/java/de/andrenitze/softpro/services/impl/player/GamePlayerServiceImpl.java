package de.andrenitze.softpro.services.impl.player;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.WebSocket;

import de.andrenitze.softpro.domains.employees.TalentMarket;
import de.andrenitze.softpro.domains.players.Player;
import de.andrenitze.softpro.services.GamePlayerService;
import java.util.*;

import static de.andrenitze.softpro.GameServer.RANDOM;
import static de.andrenitze.softpro.GameServer.gson;

@Slf4j
@RequiredArgsConstructor
public class GamePlayerServiceImpl extends BasePlayerService implements GamePlayerService {
    public static final int MAX_NUMBER_OF_PLAYERS_PER_GAME = 4;
    private final TalentMarket talentMarket;
    private final Map<UUID, Player> previousPlayerStates = new HashMap<>();

    @Override
    public void addPlayer(WebSocket webSocket, Player player) {
        try {
            if (hasWebSocket(webSocket)) {
                log.warn("Player already exists in the game. Ignoring request to add player.");
                return;
            } else if (getPlayers().size() >= MAX_NUMBER_OF_PLAYERS_PER_GAME) {
                log.warn("Game is full. Cannot add player.");
                return;
            }

            players.put(webSocket, player);
            log.debug("Added player {} to game.", player.getId());
        } catch (Exception e) {
            log.error("Could not add player to game: {}", e.getMessage());
        }
    }

    public void generateFirstEmployeesForPlayers() {
        // Remove any existing employees from the player
        getPlayers().forEach((ignored, player) -> player.getEmployees().clear());

        // Generate first employees for all players (necessary for Level 2)
        getPlayers().forEach((ignored, player) -> talentMarket.generateFirstEmployees().forEach(
                player::addEmployee
        ));
    }

    @Override
    public Player getRandomPlayer() {
        List<Player> playerList = new ArrayList<>(getPlayers().values());
        if (playerList.isEmpty()) {
            log.error("Player list is empty. Cannot select a random player.");
            return null;
        }
        return playerList.get(RANDOM.nextInt(playerList.size()));
    }


    public Player getPreviousState(Player player) {
        return previousPlayerStates.get(player.getId());
    }

    public void updatePreviousState(Player player) {
        // Deep copy the player object to avoid reference issues
        previousPlayerStates.put(player.getId(), deepCopy(player));
    }

    private Player deepCopy(Player player) {
        String json = gson.toJson(player);
        return gson.fromJson(json, Player.class);
    }
}