package de.andrenitze.softpro.services.impl;

import de.andrenitze.softpro.Player;
import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.employees.SalaryHistoryEntry;
import de.andrenitze.softpro.domains.employees.StatusEffect;
import de.andrenitze.softpro.domains.employees.StatusEffectType;
import de.andrenitze.softpro.domains.objectives.Mission;
import de.andrenitze.softpro.domains.objectives.Objective;
import de.andrenitze.softpro.domains.objectives.ObjectiveId;
import de.andrenitze.softpro.domains.projects.Project;
import de.andrenitze.softpro.domains.skills.Skill;
import de.andrenitze.softpro.services.GameLifeCycleService;
import de.andrenitze.softpro.services.ObjectiveService;
import de.andrenitze.softpro.services.ProjectEmployeeMappingService;
import de.andrenitze.softpro.services.impl.player.GamePlayerServiceImpl;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.andrenitze.softpro.Main.logger;

@Service
@Primary
public class ObjectiveServiceImpl implements ObjectiveService {
    private final GamePlayerServiceImpl playerService;
    private final GameLifeCycleService lifeCycleService;
    private final ProjectServiceImpl projectService;
    private final SkillServiceImpl skillService;
    private final ProjectEmployeeMappingService projectEmployeeService;

    public ObjectiveServiceImpl(@Lazy GamePlayerServiceImpl playerService, GameLifeCycleService lifeCycleService,
                                ProjectServiceImpl projectService, SkillServiceImpl skillService,
                                ProjectEmployeeMappingService projectEmployeeService) {
        this.playerService = playerService;
        this.lifeCycleService = lifeCycleService;
        this.projectService = projectService;
        this.skillService = skillService;
        this.projectEmployeeService = projectEmployeeService;
    }

    public Map<Player, List<Objective>> getNewObjectives(int currentTick) {
        Map<Player, List<Objective>> newObjectivesMap = new HashMap<>();
        playerService.getPlayers().forEach((_, player) -> {
            List<Objective> newObjectives = player.getNewObjectivesByTick(currentTick);
            if (!newObjectives.isEmpty()) {
                logger.debug("Found {} new objectives for player.", newObjectives.size());
                newObjectivesMap.put(player, newObjectives);
            }
        });
        return newObjectivesMap;
    }

    public boolean areThereObjectivesUpdates(Player player) {
        try {
        List<Project> projects = projectService.getProjects();
        Map<Project, ArrayList<Employee>> projectEmployeesMap = projectEmployeeService.getProjectEmployeesMap();
        boolean objectivesUpdated = false;
        for (Objective objective : player.getObjectivesUntilThisTick(lifeCycleService.getTick())) {
            if (objective.isCompleted()) {
                continue;
            }
            boolean wasUpdated = checkObjective(objective, player, projects, projectEmployeesMap, skillService);
            if (wasUpdated) {
                objectivesUpdated = true;
            }
        }

        return objectivesUpdated;
        } catch (Exception e) {
            logger.error("Error in ObjectiveServiceImpl.areThereObjectivesUpdates: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkObjective(Objective objective, Player player, List<Project> projects,
                                   Map<Project, ArrayList<Employee>> projectEmployeesMap,
                                   SkillServiceImpl skillService) {
        ObjectiveId objectiveId = ObjectiveId.fromId(objective.getId());
        return switch (objectiveId) {
            case OBJECTIVE_11 -> checkObjective11(player, projects, objective);
            case OBJECTIVE_12 -> checkObjective12(player, projectEmployeesMap, objective);
            case OBJECTIVE_13 -> checkObjective13(player, projectEmployeesMap, objective);
            case OBJECTIVE_14 -> checkObjective14(player, projects, objective);
            case OBJECTIVE_15 -> checkObjective15(player, objective);
            case OBJECTIVE_16 -> checkObjective16(player, skillService, objective);
            case OBJECTIVE_17 -> checkObjective17(player, projects, objective);
            case OBJECTIVE_210 -> checkObjective210(player, skillService, objective);
            case OBJECTIVE_220 -> checkObjective220(player, skillService, objective);
            case OBJECTIVE_222 -> checkObjective222(player, objective);
            case OBJECTIVE_223 -> checkObjective223(player, objective);
            case OBJECTIVE_230 -> checkObjective230(player, objective);
            case OBJECTIVE_231 -> checkObjective231(player, skillService, objective);
            case OBJECTIVE_31 -> checkObjective31(player, projects, objective);
            default -> false;
        };
    }

    private boolean checkObjective11(Player player, List<Project> projects, Objective objective) {
        if (projects.stream().anyMatch(project -> project.getInvolvedPlayers().contains(player))) {
            objective.setCompletedAt(lifeCycleService.getTick());
            logger.debug("Objective 11 completed.");
            return true;
        }
        return false;
    }

    private boolean checkObjective12(Player player, Map<Project, ArrayList<Employee>> projectEmployeesMap, Objective objective) {
        if (projectEmployeesMap.values().stream().anyMatch(employees -> employees.contains(player.getEmployees().getFirst()))) {
            objective.setCompletedAt(lifeCycleService.getTick());
            logger.debug("Objective 12 completed.");
            return true;
        }
        return false;
    }

    private boolean checkObjective13(Player player, Map<Project, ArrayList<Employee>> projectEmployeesMap, Objective objective) {
        if (projectEmployeesMap.values().stream().anyMatch(employees -> employees.contains(player.getEmployees().getFirst()))
                && projectEmployeesMap.keySet().stream().anyMatch(project -> project.getStartedAt() != 0)) {
            objective.setCompletedAt(lifeCycleService.getTick());
            logger.debug("Objective 13 completed.");
            return true;
        }
        return false;
    }

    private boolean checkObjective14(Player player, List<Project> projects, Objective objective) {
        if (projects.stream().anyMatch(project -> project.isCompleted() && project.playerWasInvolved(player))) {
            objective.setCompletedAt(lifeCycleService.getTick());
            logger.debug("Objective 14 completed.");
            return true;
        }
        return false;
    }

    private boolean checkObjective15(Player player, Objective objective) {
        if (objective.getCompletedSteps() >= objective.getTotalSteps()) {
            objective.setCompletedAt(lifeCycleService.getTick());
            return true;
        } else if (player.getXp() != objective.getCompletedSteps()) {
            objective.setCompletedSteps(player.getXp());
            return true;
        }
        return false;
    }

    private boolean checkObjective16(Player player, SkillServiceImpl skillService, Objective objective) {
        Map<String, Skill> skills = skillService.getSkillsByPlayer(player);
        if (skills.containsKey("pmo") && skills.get("pmo").isUnlocked()) {
            objective.setCompletedAt(lifeCycleService.getTick());
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
                .toList();

        if (objective.getCompletedSteps() != relevantProjects.size()) {
            objective.setCompletedSteps(relevantProjects.size());
            logStepCompletion(objective);
            return true;
        }

        if (objective.getCompletedSteps() >= objective.getTotalSteps()) {
            objective.setCompletedAt(lifeCycleService.getTick());
            logger.debug("Objective {} completed.", objective.getId());
            return true;
        }
        return false;
    }

    private boolean checkObjective210(Player player, SkillServiceImpl skillService, Objective objective) {
        Map<String, Skill> skills = skillService.getSkillsByPlayer(player);
        if ((skills.containsKey("team-spirit") && skills.get("team-spirit").isUnlocked()) ||
                (skills.containsKey("crunch-mode") && skills.get("crunch-mode").isUnlocked())) {
            objective.setCompletedAt(lifeCycleService.getTick());
            logger.debug("Objective 210 completed.");
            return true;
        }
        return false;
    }

    private boolean checkObjective220(Player player, SkillServiceImpl skillService, Objective objective) {
        Map<String, Skill> skills = skillService.getSkillsByPlayer(player);
        if (skills.containsKey("recruiting-1") && skills.get("recruiting-1").isUnlocked()) {
            objective.setCompletedAt(lifeCycleService.getTick());
            logger.debug("Objective 220 completed.");
            return true;
        }
        return false;
    }

    private boolean checkObjective222(Player player, Objective objective) {
        if (!player.getEmployees().isEmpty()) {
            Employee employee = player.getEmployees().getFirst();
            List<SalaryHistoryEntry> history = employee.getSalaryHistory();

            if (history.size() > 1) {
                SalaryHistoryEntry initialEntry = history.getFirst();
                SalaryHistoryEntry latestEntry = history.getLast();

                if (latestEntry.salary() >= initialEntry.salary() * 1.1) {
                    objective.setCompletedAt(lifeCycleService.getTick());
                    logger.debug("Objective 222 completed: Employee received a 10% raise.");
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkObjective223(Player player, Objective objective) {
        if (!player.getEmployees().isEmpty()) {
            Employee employee = player.getEmployees().getFirst();
            List<StatusEffect> statusEffects = employee.getStatusEffects();

            if (statusEffects.stream().anyMatch(statusEffect ->
                    statusEffect.getType() == StatusEffectType.SATISFACTION
                            && statusEffect.getDescription().equals("Feels heard"))) {
                objective.setCompletedAt(lifeCycleService.getTick());
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
            objective.setCompletedAt(lifeCycleService.getTick());
            logger.debug("Objective 230 completed.");
            return true;
        }
        return false;
    }

    private boolean checkObjective231(Player player, SkillServiceImpl skillService, Objective objective) {
        Map<String, Skill> skills = skillService.getSkillsByPlayer(player);
        if (skills.containsKey("team-lead") && skills.get("team-lead").isUnlocked()) {
            objective.setCompletedAt(lifeCycleService.getTick());
            logger.debug("Objective 231 completed.");
            return true;
        }
        return false;
    }

    private boolean checkObjective31(Player player, List<Project> projects, Objective objective) {
        Mission mission = getMissionByObjective(player, objective);
        if (mission == null) return false;

        List<Project> relevantProjects = projects
                .stream()
                .filter(project -> project.isCompleted()
                        && project.getCompletedAt() > mission.getEarliestOccurrence()
                        && project.playerWasInvolved(player))
                .toList();

        if (objective.getCompletedSteps() != relevantProjects.size()) {
            objective.setCompletedSteps(relevantProjects.size());
            logStepCompletion(objective);
            return true;
        }
        return false;
    }

    private void logStepCompletion(Objective objective) {
        logger.debug("Objective {} completed steps: {}/{}",
                objective.getId(),
                objective.getCompletedSteps(),
                objective.getTotalSteps());
    }

    @Nullable
    private Mission getMissionByObjective(Player player, Objective objective) {
        // Get mission by objective
        Mission mission = player.getMissions().stream()
                .filter(m -> m.getObjectives().contains(objective))
                .findFirst()
                .orElse(null);

        if (mission == null) {
            logger.warn("Objective {} has no mission.", objective.getId());
            return null;
        }
        return mission;
    }
}