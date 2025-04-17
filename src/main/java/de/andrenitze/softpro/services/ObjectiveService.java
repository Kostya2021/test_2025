package de.andrenitze.softpro.services;

import de.andrenitze.softpro.domains.objectives.Objective;
import de.andrenitze.softpro.domains.players.Player;

import java.util.List;
import java.util.Map;

public interface ObjectiveService {
    Map<Player, List<Objective>> getNewObjectives(int currentTick);
    boolean areThereObjectivesUpdates(Player player);
}
