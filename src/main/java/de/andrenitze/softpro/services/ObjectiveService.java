package de.andrenitze.softpro.services;

import de.andrenitze.softpro.Player;
import de.andrenitze.softpro.domains.objectives.Objective;

import java.util.List;
import java.util.Map;

public interface ObjectiveService {
    Map<Player, List<Objective>> getNewObjectives(int currentTick);
}
