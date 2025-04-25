package de.andrenitze.softpro.services.impl;

import de.andrenitze.softpro.domains.objectives.Objective;
import de.andrenitze.softpro.domains.players.Player;
import de.andrenitze.softpro.domains.story.StoryElement;
import de.andrenitze.softpro.domains.story.StoryElementsLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class StoryService {
    /**
     * Loaded at level start, stays the same throughout the level.
     */
    private List<StoryElement> storyElements;

    /**
     * Map to keep track of sent elements for each player to not send them again.
      */
    private final Map<String, Set<Integer>> sentStoryElementIds = new ConcurrentHashMap<>();

    public void loadStory(int level) {
        this.storyElements = new StoryElementsLoader().getStoryElementsForLevel(level);
    }

    public List<StoryElement> getNewStoryElementsForPlayer(Player player, int currentTick) {
        List<StoryElement> newStoryElements = new ArrayList<>();
        String playerId = player.getId().toString();
        Set<Integer> sentElementIds = sentStoryElementIds.computeIfAbsent(playerId, ignored -> new HashSet<>());

        for (StoryElement element : storyElements) {
            if (!sentElementIds.contains(element.getId()) &&
                    isElementRelevantForPlayer(element, player, currentTick)) {

                element.setSent(true);
                newStoryElements.add(element);

                sentElementIds.add(element.getId());
            }
        }
        return newStoryElements;
    }

    private boolean isElementRelevantForPlayer(StoryElement element, Player player, int currentTick) {
        if (element.getEarliestOccurrence() > currentTick && element.getAfterObjective() == 0) {
            return false;
        }

        if (element.getAfterObjective() != 0) {
            for (Objective objective : player.getCompletedObjectives()) {
                if (element.getAfterObjective() == objective.getId()) {
                    return true;
                }
            }
            return false;
        }

        return true;
    }
}