package de.andrenitze.softpro.entities;
import de.andrenitze.softpro.*;
import de.andrenitze.softpro.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

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
        boolean updated = false;
        HashMap<String, Skill> skills;

        switch (objective.getId()) {
            case 11:
                if (projects.stream().anyMatch(project -> project.getInvolvedPlayers().contains(player))) {
                    objective.setCompleted();
                    logger.debug("Objective 11 completed.");
                    updated = true;
                }
                break;

            case 12:
                if (projectEmployeesMap.values().stream().anyMatch(employees ->
                        employees.contains(player.getEmployees().get(0)))) {
                    objective.setCompleted();
                    logger.debug("Objective 12 completed.");
                    updated = true;
                }
                break;

            case 13:
                if (projectEmployeesMap.values().stream().anyMatch(employees -> employees.contains(player.getEmployees().get(0)))
                        && projectEmployeesMap.keySet().stream().anyMatch(project -> project.getStartedAt() != 0)) {
                    objective.setCompleted();
                    logger.debug("Objective 13 completed.");
                    updated = true;
                }
                break;

            case 15:
                if (player.getXp() != objective.getCompletedSteps()) {
                    objective.setCompletedSteps(player.getXp());
                    updated = true;
                }

                if (player.getXp() >= 125) {
                    objective.setCompleted();
                    logger.debug("Objective 15 completed.");
                    updated = true;
                }
                break;

            case 16:
                skills = skillsManager.getSkillsByPlayer(player);
                if (skills.containsKey("pmo") && skills.get("pmo").isUnlocked()) {
                    objective.setCompleted();
                    logger.debug("Objective 16 completed.");
                    updated = true;
                }
                break;

            case 210:
                skills = skillsManager.getSkillsByPlayer(player);
                if ((skills.containsKey("team-spirit") && skills.get("team-spirit").isUnlocked()) ||
                        (skills.containsKey("crunch-mode") && skills.get("crunch-mode").isUnlocked())) {
                    objective.setCompleted();
                    logger.debug("Objective 21 completed.");
                    updated = true;
                }
                break;

            case 14:
            case 17:
            case 31:
                Mission mission = game.getMissionByObjective(player, objective);
                if (mission == null) return false;

                ArrayList<Project> relevantProjects = (ArrayList<Project>) projects
                        .stream()
                        .filter(project -> project.isCompleted()
                                && project.getCompletedAt() > mission.getEarliestOccurrence()
                                && project.playerWasInvolved(player))
                        .toList();

                if (objective.getCompletedSteps() != relevantProjects.size()) {
                    objective.setCompletedSteps(relevantProjects.size());
                    logger.debug("Objective {} completed steps: {}/{}",
                            objective.getId(),
                            objective.getCompletedSteps(),
                            objective.getTotalSteps());
                    updated = true;
                }
                break;

            case 220:
                // Unlock skill "Recruiting I"
                skills = skillsManager.getSkillsByPlayer(player);
                if (skills.containsKey("recruiting-1") && skills.get("recruiting-1").isUnlocked()) {
                    objective.setCompleted();
                    logger.debug("Objective 220 completed.");
                    updated = true;
                }
                break;

            case 222:
                // Objective: Give your employee a 10% raise
                if (!player.getEmployees().isEmpty()) {
                    Employee employee = player.getEmployees().get(0);
                    List<SalaryHistoryEntry> history = employee.getSalaryHistory();

                    if (history.size() > 1) {
                        SalaryHistoryEntry initialEntry = history.get(0);
                        SalaryHistoryEntry latestEntry = history.get(history.size() - 1);

                        if (latestEntry.getSalary() >= initialEntry.getSalary() * 1.1) {
                            objective.setCompleted();
                            logger.debug("Objective 222 completed: Employee received a 10% raise.");
                            updated = true;
                        }
                    }
                }
                break;

            case 223:
                // Objective: Have a one-to-one with your employee
                if (!player.getEmployees().isEmpty()) {
                    Employee employee = player.getEmployees().get(0);
                    List<StatusEffect> statusEffects = employee.getStatusEffects();

                    if (statusEffects.stream().anyMatch(statusEffect ->
                            statusEffect.getType() == StatusEffectType.SATISFACTION
                                    && statusEffect.getDescription().equals("Feels heard"))) {
                        objective.setCompleted();
                        logger.debug("Objective 223 completed: One-to-one with employee.");
                        updated = true;
                    }
                }
                break;

            case 224:
                // Objective: Train your employee in project management basics
                // Not implemented yet (no training feature)
                break;

            case 230:
                // Objective: Build a team of 5 employees
                if (player.getEmployees().size() != objective.getCompletedSteps()) {
                    objective.setCompletedSteps(player.getEmployees().size());
                    updated = true;
                }

                if (player.getEmployees().size() >= 5) {
                    objective.setCompleted();
                    logger.debug("Objective 230 completed.");
                    updated = true;
                }
                break;

            case 231:
                // Objective: Unlock skill "Team Lead"
                skills = skillsManager.getSkillsByPlayer(player);
                if (skills.containsKey("team-lead") && skills.get("team-lead").isUnlocked()) {
                    objective.setCompleted();
                    logger.debug("Objective 231 completed.");
                    updated = true;
                }
                break;

            case 232:
                // Objective: Let the team lead staff a project
                // Not implemented yet (no team lead feature)
                break;

            case 240:
                // Objective: Fire an employee
                // Check if player has fired an employee
                // Don't know how to check this yet...
                break;

            case 241:
                // Objective: Cancel the messy project
                // Not implemented yet (no project cancellation feature)
                break;
        }

        return updated;
    }

    private void notifyPlayerAboutObjectives(Player player) {
        logger.debug("Sending updated objectives to player.");
        List<Objective> allActiveObjectives = player.getObjectivesUntilThisTick(currentTick);
        GameEvent<List<Objective>> objectivesUpdatedEvent = new GameEvent<>(EventType.OBJECTIVES_UPDATED);
        objectivesUpdatedEvent.setPayload(allActiveObjectives);
        game.sendMessageToPlayer(player, GSON.toJson(objectivesUpdatedEvent));
    }
}