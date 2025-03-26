package de.andrenitze.softpro.services.impl;

import de.andrenitze.softpro.Player;
import de.andrenitze.softpro.domains.objectives.Objective;
import de.andrenitze.softpro.services.ObjectiveService;
import de.andrenitze.softpro.services.impl.player.GamePlayerServiceImpl;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.andrenitze.softpro.Main.logger;

@Service
public class ObjectiveServiceImpl implements ObjectiveService {
    private final GamePlayerServiceImpl playersService;

    public ObjectiveServiceImpl(GamePlayerServiceImpl playerService) {
        this.playersService = playerService;
    }

    public Map<Player, List<Objective>> getNewObjectives(int currentTick) {
        Map<Player, List<Objective>> newObjectivesMap = new HashMap<>();
        playersService.getPlayers().forEach((_, player) -> {
            List<Objective> newObjectives = player.getNewObjectivesByTick(currentTick);
            if (!newObjectives.isEmpty()) {
                logger.debug("Found {} new objectives for player.", newObjectives.size());
                newObjectivesMap.put(player, newObjectives);
            }
        });
        return newObjectivesMap;
    }
}