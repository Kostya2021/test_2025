package de.andrenitze.softpro.services.impl.player;

import de.andrenitze.softpro.Player;
import de.andrenitze.softpro.TalentMarket;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static de.andrenitze.softpro.GameServer.RANDOM;

@Service
public class GamePlayerServiceImpl extends BasePlayerService {
    private final TalentMarket talentMarket;

    public GamePlayerServiceImpl(TalentMarket talentMarket) {
        this.talentMarket = talentMarket;
    }

    public void generateFirstEmployeesForPlayers() {
        // Remove any existing employees from the player
        getPlayers().forEach((_, player) -> player.getEmployees().clear());

        // Generate first employees for all players (necessary for Level 2)
        getPlayers().forEach((_, player) -> talentMarket.generateFirstEmployees().forEach(
                employee -> player.addEmployee(employee, 0)
        ));
    }

    public boolean isPlayerInAnyGame(Player player) {
        return getPlayers().containsValue(player);
    }

    public Player getRandomPlayer() {
        List<Player> playerList = new ArrayList<>(getPlayers().values());
        if (playerList.isEmpty()) {
            return null;
        }
        return playerList.get(RANDOM.nextInt(playerList.size()));
    }
}