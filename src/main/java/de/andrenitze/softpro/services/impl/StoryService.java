package de.andrenitze.softpro.services.impl;

import de.andrenitze.softpro.Player;
import de.andrenitze.softpro.domains.objectives.Objective;
import de.andrenitze.softpro.domains.story.StoryElement;
import de.andrenitze.softpro.domains.story.StoryElementsLoader;

import java.util.*;

public class StoryService {
    private List<StoryElement> storyElements;
    private final Map<Player, Set<StoryElement>> sentStoryElements = new HashMap<>();

    public void loadStory(int level) {
        this.storyElements = new StoryElementsLoader().getStoryElementsForLevel(level);
    }

    public List<StoryElement> getStoryElementsForPlayer(Player player, int currentTick) {
        List<StoryElement> relevantStoryElements = getRelevantStoryElements(currentTick);
        List<StoryElement> playerStoryElements = new ArrayList<>();

        if (relevantStoryElements.isEmpty()) {
            return playerStoryElements;
        }

        playerStoryElements = getPlayerStoryElements(relevantStoryElements, player);
        markStoryElementsAsSent(player, playerStoryElements);

        return playerStoryElements;
    }

    private List<StoryElement> getRelevantStoryElements(int currentTick) {
        List<StoryElement> relevantStoryElements = new ArrayList<>();
        this.storyElements.forEach(element -> {
            if (element.getEarliestOccurrence() <= currentTick || element.getAfterObjective() != 0) {
                relevantStoryElements.add(element);
            }
        });
        return relevantStoryElements;
    }

    private List<StoryElement> getPlayerStoryElements(List<StoryElement> relevantStoryElements, Player player) {
        List<StoryElement> thisPlayersStoryElements = new ArrayList<>();
        Set<StoryElement> sentElements = sentStoryElements.getOrDefault(player, new HashSet<>());

        relevantStoryElements.forEach(storyElement -> {
            if (!sentElements.contains(storyElement)) {
                ArrayList<Objective> completedObjectives = (ArrayList<Objective>) player.getCompletedObjectives();
                if (storyElement.getAfterObjective() != 0) {
                    completedObjectives.forEach(objective -> {
                        if (storyElement.getAfterObjective() == objective.getId()) {
                            thisPlayersStoryElements.add(storyElement);
                        }
                    });
                } else {
                    thisPlayersStoryElements.add(storyElement);
                }
            }
        });
        return thisPlayersStoryElements;
    }

    private void markStoryElementsAsSent(Player player, List<StoryElement> storyElements) {
        Set<StoryElement> sentElements = sentStoryElements.getOrDefault(player, new HashSet<>());
        sentElements.addAll(storyElements);
        sentStoryElements.put(player, sentElements);
    }


}