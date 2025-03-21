package de.andrenitze.softpro.domains.objectives;

import de.andrenitze.softpro.*;
import de.andrenitze.softpro.domains.GameEvent;
import de.andrenitze.softpro.domains.employees.SalaryHistoryEntry;
import de.andrenitze.softpro.domains.skills.Skill;
import de.andrenitze.softpro.domains.employees.Employee;
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
        List<Project> projects = game.getProjectService().getProjects();
        Map<Project, ArrayList<Employee>> projectEmployeesMap = game.getProjectEmployeesMap();
        SkillsManager skillsManager = game.getSkillsManager();

        boolean objectivesUpdated = false;

        for (Objective objective : player.getObjectivesUntilThisTick(this.currentTick)) {
            if (objective.isCompleted()) {
                continue;
            }

            boolean wasUpdated = checkObjective(objective, player, projects, projectEmployeesMap, skillsManager);
            if (wasUpdated) {
                objectivesUpdated = true;
            }
        }

        if (objectivesUpdated) {
            notifyPlayerAboutObjectives(player);
        }
    }

    private boolean checkObjective(Objective objective, Player player, List<Project> projects,
                                   Map<Project, ArrayList<Employee>> projectEmployeesMap,
                                   SkillsManager skillsManager) {
        ObjectiveId objectiveId = ObjectiveId.fromId(objective.getId());
        return switch (objectiveId) {
            case OBJECTIVE_11 -> checkObjective11(player, projects, objective);
            case OBJECTIVE_12 -> checkObjective12(player, projectEmployeesMap, objective);
            case OBJECTIVE_13 -> checkObjective13(player, projectEmployeesMap, objective);
            case OBJECTIVE_14, OBJECTIVE_15 -> checkObjective14And15(player, objective);
            case OBJECTIVE_16 -> checkObjective16(player, skillsManager, objective);
            case OBJECTIVE_17 -> checkObjective17(player, projects, objective);
            case OBJECTIVE_210 -> checkObjective210(player, skillsManager, objective);
            case OBJECTIVE_220 -> checkObjective220(player, skillsManager, objective);
            case OBJECTIVE_222 -> checkObjective222(player, objective);
            case OBJECTIVE_223 -> checkObjective223(player, objective);
            case OBJECTIVE_230 -> checkObjective230(player, objective);
            case OBJECTIVE_231 -> checkObjective231(player, skillsManager, objective);
            case OBJECTIVE_31 -> checkObjective31(player, projects, objective);
            default -> false;
        };
    }

    private boolean checkObjective11(Player player, List<Project> projects, Objective objective) {
        if (projects.stream().anyMatch(project -> project.getInvolvedPlayers().contains(player))) {
            objective.setCompleted(currentTick);
            logger.debug("Objective 11 completed.");
            return true;
        }
        return false;
    }

    private boolean checkObjective12(Player player, Map<Project, ArrayList<Employee>> projectEmployeesMap, Objective objective) {
        if (projectEmployeesMap.values().stream().anyMatch(employees -> employees.contains(player.getEmployees().get(0)))) {
            objective.setCompleted(currentTick);
            logger.debug("Objective 12 completed.");
            return true;
        }
        return false;
    }

    private boolean checkObjective13(Player player, Map<Project, ArrayList<Employee>> projectEmployeesMap, Objective objective) {
        if (projectEmployeesMap.values().stream().anyMatch(employees -> employees.contains(player.getEmployees().get(0)))
                && projectEmployeesMap.keySet().stream().anyMatch(project -> project.getStartedAt() != 0)) {
            objective.setCompleted(currentTick);
            logger.debug("Objective 13 completed.");
            return true;
        }
        return false;
    }

    private boolean checkObjective14And15(Player player, Objective objective) {
        if (objective.getCompletedSteps() >= objective.getTotalSteps()) {
            objective.setCompleted(currentTick);
            return true;
        } else if (player.getXp() != objective.getCompletedSteps()) {
            objective.setCompletedSteps(player.getXp());
            return true;
        }
        return false;
    }

    private boolean checkObjective16(Player player, SkillsManager skillsManager, Objective objective) {
        HashMap<String, Skill> skills = skillsManager.getSkillsByPlayer(player);
        if (skills.containsKey("pmo") && skills.get("pmo").isUnlocked()) {
            objective.setCompleted(currentTick);
            logger.debug("Objective 16 completed.");
            return true;
        }
        return false;
    }

    private boolean checkObjective17(Player player, List<Project> projects, Objective objective) {
        Objective previousObjective = player.getObjectiveById(objective.getId() - 1);
        if (previousObjective == null) return false;

        List<Project> relevantProjects = projects
                .stream()
                .filter(project -> project.isCompleted()
                        && project.getCompletedAt() > previousObjective.getCompletedAt()
                        && project.playerWasInvolved(player))
                .collect(Collectors.toList());

        if (objective.getCompletedSteps() != relevantProjects.size()) {
            objective.setCompletedSteps(relevantProjects.size());
            debugLogStepCompletion(objective);
            return true;
        }

        if (objective.getCompletedSteps() >= objective.getTotalSteps()) {
            objective.setCompleted(currentTick);
            logger.debug("Objective {} completed.", objective.getId());
            return true;
        }
        return false;
    }

    private boolean checkObjective210(Player player, SkillsManager skillsManager, Objective objective) {
        HashMap<String, Skill> skills = skillsManager.getSkillsByPlayer(player);
        if ((skills.containsKey("team-spirit") && skills.get("team-spirit").isUnlocked()) ||
                (skills.containsKey("crunch-mode") && skills.get("crunch-mode").isUnlocked())) {
            objective.setCompleted(currentTick);
            logger.debug("Objective 210 completed.");
            return true;
        }
        return false;
    }

    private boolean checkObjective220(Player player, SkillsManager skillsManager, Objective objective) {
        HashMap<String, Skill> skills = skillsManager.getSkillsByPlayer(player);
        if (skills.containsKey("recruiting-1") && skills.get("recruiting-1").isUnlocked()) {
            objective.setCompleted(currentTick);
            logger.debug("Objective 220 completed.");
            return true;
        }
        return false;
    }

    private boolean checkObjective222(Player player, Objective objective) {
        if (!player.getEmployees().isEmpty()) {
            Employee employee = player.getEmployees().get(0);
            List<SalaryHistoryEntry> history = employee.getSalaryHistory();

            if (history.size() > 1) {
                SalaryHistoryEntry initialEntry = history.get(0);
                SalaryHistoryEntry latestEntry = history.get(history.size() - 1);

                if (latestEntry.salary() >= initialEntry.salary() * 1.1) {
                    objective.setCompleted(currentTick);
                    logger.debug("Objective 222 completed: Employee received a 10% raise.");
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkObjective223(Player player, Objective objective) {
        if (!player.getEmployees().isEmpty()) {
            Employee employee = player.getEmployees().get(0);
            List<StatusEffect> statusEffects = employee.getStatusEffects();

            if (statusEffects.stream().anyMatch(statusEffect ->
                    statusEffect.getType() == StatusEffectType.SATISFACTION
                            && statusEffect.getDescription().equals("Feels heard"))) {
                objective.setCompleted(currentTick);
                logger.debug("Objective 223 completed: One-to-one with employee.");
                return true;
            }
        }
        return false;
    }

    private boolean checkObjective230(Player player, Objective objective) {
        if (player.getEmployees().size() != objective.getCompletedSteps()) {
            objective.setCompletedSteps(player.getEmployees().size());
            return true;
        }

        if (player.getEmployees().size() >= 5) {
            objective.setCompleted(currentTick);
            logger.debug("Objective 230 completed.");
            return true;
        }
        return false;
    }

    private boolean checkObjective231(Player player, SkillsManager skillsManager, Objective objective) {
        HashMap<String, Skill> skills = skillsManager.getSkillsByPlayer(player);
        if (skills.containsKey("team-lead") && skills.get("team-lead").isUnlocked()) {
            objective.setCompleted(currentTick);
            logger.debug("Objective 231 completed.");
            return true;
        }
        return false;
    }

    private boolean checkObjective31(Player player, List<Project> projects, Objective objective) {
        Mission mission = game.getMissionByObjective(player, objective);
        if (mission == null) return false;

        List<Project> relevantProjects = projects
                .stream()
                .filter(project -> project.isCompleted()
                        && project.getCompletedAt() > mission.getEarliestOccurrence()
                        && project.playerWasInvolved(player))
                .collect(Collectors.toList());

        if (objective.getCompletedSteps() != relevantProjects.size()) {
            objective.setCompletedSteps(relevantProjects.size());
            debugLogStepCompletion(objective);
            return true;
        }
        return false;
    }

    private void debugLogStepCompletion(Objective objective) {
        logger.debug("Objective {} completed steps: {}/{}",
                objective.getId(),
                objective.getCompletedSteps(),
                objective.getTotalSteps());
    }

    private void notifyPlayerAboutObjectives(Player player) {
        logger.debug("Sending updated objectives to player.");
        List<Objective> allActiveObjectives = player.getObjectivesUntilThisTick(currentTick);
        GameEvent<List<Objective>> objectivesUpdatedEvent = new GameEvent<>(EventType.OBJECTIVES_UPDATED);
        objectivesUpdatedEvent.setPayload(allActiveObjectives);
        game.getMessagingService().sendMessageToPlayer(player, GSON.toJson(objectivesUpdatedEvent));
    }
}