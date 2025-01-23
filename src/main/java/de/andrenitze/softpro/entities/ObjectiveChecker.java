package de.andrenitze.softpro.entities;
import de.andrenitze.softpro.*;
import de.andrenitze.softpro.events.GameEvent;
import de.andrenitze.softpro.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static de.andrenitze.softpro.GameServer.GSON;


public class ObjectiveChecker {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final Game game;
    private final int currentTick;

    public ObjectiveChecker(Game game) {
        this.game = game;
        this.currentTick = game.getCurrentTick();
    }

    public void checkObjectives(Player player) {
        List<Project> projects = game.getProjects();
        Map<Project, ArrayList<Employee>> projectEmployeesMap = game.getProjectEmployeesMap();
        SkillsManager skillsManager = game.getSkillsManager();

        boolean updatedNeeded;

        for (Objective objective : player.getObjectivesUntilThisTick(this.currentTick)) {
            updatedNeeded = false;

            if (objective.isCompleted()) {
                continue;
            }

            if (objective.getId() == 11) {
                if (projects.stream().anyMatch(project -> project.getInvolvedPlayers().contains(player))) {
                    objective.markAsCompleted();
                    logger.debug("Objective 11 completed.");
                    updatedNeeded = true;
                }
            } else if (objective.getId() == 12) {
                if (projectEmployeesMap.values().stream().anyMatch(employees -> employees.contains(player.getEmployees().get(0)))) {
                    objective.markAsCompleted();
                    logger.debug("Objective 12 completed.");
                    updatedNeeded = true;
                }
            } else if (objective.getId() == 13) {
                if (projectEmployeesMap.values().stream().anyMatch(employees -> employees.contains(player.getEmployees().get(0)))
                        && projectEmployeesMap.keySet().stream().anyMatch(project -> project.getStartedAt() != 0)) {
                    objective.markAsCompleted();
                    logger.debug("Objective 13 completed.");
                    updatedNeeded = true;
                }
            } else if (objective.getId() == 15) {
                if (player.getXp() != objective.getCompletedSteps()) {
                    objective.setCompletedSteps(player.getXp());
                    updatedNeeded = true;
                }

                if (player.getXp() >= 125) {
                    objective.markAsCompleted();
                    logger.debug("Objective 15 completed.");
                    updatedNeeded = true;
                }
            } else if (objective.getId() == 16) {
                HashMap<String, Skill> skills = skillsManager.getSkillsByPlayer(player);
                if (skills.containsKey("pmo") && skills.get("pmo").isUnlocked()) {
                    objective.markAsCompleted();
                    logger.debug("Objective 16 completed.");
                    updatedNeeded = true;
                }
            } else if (objective.getId() == 210) {
                HashMap<String, Skill> skills = skillsManager.getSkillsByPlayer(player);
                if (skills.containsKey("team-spirit") && skills.get("team-spirit").isUnlocked() ||
                        skills.containsKey("crunch-mode") && skills.get("crunch-mode").isUnlocked()) {
                    objective.markAsCompleted();
                    logger.debug("Objective 21 completed.");
                    updatedNeeded = true;
                }
            } else if (objective.getId() == 14 || objective.getId() == 17 || objective.getId() == 31) {
                Mission mission = game.getMissionByObjective(player, objective);
                if (mission == null) return;

                ArrayList<Project> relevantProjects = (ArrayList<Project>) projects
                        .stream()
                        .filter(project -> project.isCompleted()
                                && project.getCompletedAt() > mission.getEarliestOccurrence()
                                && project.playerWasInvolved(player))
                        .collect(Collectors.toList());

                if (objective.getCompletedSteps() != relevantProjects.size()) {
                    objective.setCompletedSteps(relevantProjects.size());
                    logger.debug("Objective {} completed steps: {}/{}",
                            objective.getId(),
                            objective.getCompletedSteps(),
                            objective.getTotalSteps());
                    updatedNeeded = true;
                }
            }

            if (updatedNeeded) {
                logger.debug("Sending updated objectives to player.");
                List<Objective> allActiveObjectives = player.getObjectivesUntilThisTick(currentTick);
                GameEvent<List<Objective>> objectivesUpdatedEvent = new GameEvent<>(EventType.OBJECTIVES_UPDATED);
                objectivesUpdatedEvent.setPayload(allActiveObjectives);
                game.sendMessageToPlayer(player, GSON.toJson(objectivesUpdatedEvent));
            }
        }
    }
}