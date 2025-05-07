package de.andrenitze.softpro.events;

import com.google.gson.reflect.TypeToken;
import de.andrenitze.softpro.services.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.WebSocket;

import de.andrenitze.softpro.GameServer;
import de.andrenitze.softpro.domains.accounting.AccountCategory;
import de.andrenitze.softpro.domains.accounting.AccountingEntry;
import de.andrenitze.softpro.domains.accounting.TransactionType;
import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.employees.TalentMarket;
import de.andrenitze.softpro.domains.players.Player;
import de.andrenitze.softpro.domains.projects.Problem;
import de.andrenitze.softpro.domains.projects.Project;
import de.andrenitze.softpro.services.impl.AccountingServiceImpl;
import de.andrenitze.softpro.services.impl.GameLifeCycleService;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Slf4j
public class GameEventHandler {
    public static final String TEAM_SPIRIT = "team-spirit";
    public static final String CRUNCH_MODE = "crunch-mode";
    public static final String PARTY_CONTRACTOR = "contractor";
    public static final String PARTY_CLIENT = "client";
    public static final String EMPLOYEE_ID = "employeeId";
    public static final String PROJECT_ID = "projectId";
    public static final String STARTED_AT = "startedAt";
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final PlayerService playerService;
    private final EmployeeService employeeService;
    private final TalentMarket talentMarket;
    private final ProjectService projectService;
    private final MessagingService messagingService;
    private final GameLifeCycleService gameLifeCycleService;
    private final SkillService skillService;
    private final ProjectEmployeeMappingService projectEmployeeService;
    private final AccountingServiceImpl accountingService;

    public void handleEvent(WebSocket websocket, String message) {
        GameEvent<?> event = GameServer.getGson().fromJson(message, GameEvent.class);
        log.debug("Identified event: {}", event.getType());

        if (event.getType() == null) {
            log.warn("Received event with unknown type: {}", message);
            return;
        }

        try {
            switch (event.getType()) {
                case PAUSE -> gameLifeCycleService.pause();
                case RESUME -> gameLifeCycleService.resume();
                case PLAYER_NAME_UPDATED -> handlePlayerNameUpdatedEvent(websocket);
                case JOIN_TENDER -> handleJoinTenderEvent(websocket, message, gameLifeCycleService.getTick());
                case ASSIGN_EMPLOYEE -> handleAssignEmployeeEvent(websocket, message);
                case UNASSIGN_EMPLOYEE -> handleUnassignEmployeeEvent(websocket, message);
                case SKILL_UNLOCKED -> handleSkillUnlockedEvent(websocket, message);
                case HIRE_TALENT -> handleHireTalentEvent(websocket, message);
                case RISK_ASSESSMENT_REQUESTED -> handleRiskAssessmentRequestedEvent(websocket, message);
                case EMPLOYEE_DISMISSED -> handleEmployeeDismissedEvent(websocket, message);
                case PROJECT_STARTED -> handleProjectStartedEvent(message);
                case EMPLOYEE_SALARY_UPDATED -> handleEmployeeSalaryUpdatedEvent(websocket, message);
                case EFFECT_ENABLED -> handleEffectEnabledEvent(websocket, message);
                case PROBLEM_SOLVED -> handleProblemSolvedEvent(message);
                case ONE_TO_ONE_MEETING -> handleOneToOneMeetingEvent(websocket, message);
                case TEAM_ESTIMATE_REQUESTED -> handleTeamEstimateRequestedEvent(websocket, message);
                case PROJECT_CANCEL_REQUESTED -> handleProjectCancelRequestedEvent(websocket, message);
                case EMPLOYEE_TRAINING_REQUESTED -> handleEmployeeTrainingRequestedEvent(websocket, message);
                default -> log.warn("Received unknown event type: {}", event.getType());
            }
        } catch (Exception e) {
            log.error("Error handling event {}: {}", event.getType(), e.getMessage());
        }
    }

    private void handlePlayerNameUpdatedEvent(WebSocket websocket) {
        // Use player's name as first employee's name
        if (gameLifeCycleService.getLevel() == 1) {
            // Find first employee of player
            Player player = playerService.getPlayer(websocket);
            Employee employee = player.getEmployees().getFirst();

            // Change employee's name to player's name
            if (employee != null) {
                // The player's name was just changed, so we can use it directly
                String name = player.getName();
                employee.setFirstName(name.split(" ")[0]);
                employee.setLastName(name.split(" ")[1]);
                messagingService.sendEmployeeUpdate(player, employee);
            } else {
                log.warn("Could not update employee name. Employee not found.");
            }
        }
    }

    private void handleEmployeeTrainingRequestedEvent(WebSocket websocket, String message) {
        Type payloadType = new TypeToken<GameEvent<HashMap<String, String>>>() {}.getType();
        GameEvent<HashMap<String, String>> trainingEvent = GameServer.getGson().fromJson(message, payloadType);
        String training = trainingEvent.getPayload().get("trainingId");
        int employeeId = Integer.parseInt(trainingEvent.getPayload().get(EMPLOYEE_ID));

        if (training == null) {
            log.warn("Could not train employee. Training type is null.");
            return;
        }

        Player player = playerService.getPlayer(websocket);
        Employee employee = player.getEmployeeById(employeeId);

        if (employee == null) {
            log.warn("Could not train employee. Employee {} not found.", employeeId);
            return;
        }

        // Charge for the training
        AccountingEntry accountingEntry = new AccountingEntry(player, gameLifeCycleService.getTick(),
                gameLifeCycleService.getLevel(), 2000, AccountCategory.TRAINING, TransactionType.DEBIT,
                "Training for employee " + employee.getName());
        accountingService.addEntry(accountingEntry);
        player.subtractFunds(accountingEntry.getAmount());

        employee.train(training);
        log.debug("Player {} trained employee {} - {}", player.getId(), employee.getId(), employee.getName());
    }

    private void handleJoinTenderEvent(WebSocket websocket, String message, int gameTick) {
        Type payloadType = new TypeToken<GameEvent<Integer>>() {}.getType();
        GameEvent<Integer> joinTenderEvent = GameServer.getGson().fromJson(message, payloadType);
        int projectId = joinTenderEvent.getPayload();

        try {
            for (Project project : projectService.getProjects()) {
                if (project.getId() == projectId) {
                    Player player = playerService.getPlayer(websocket);
                    project.addParty(player);

                    if (project.hasNoTenderProcess()) {
                        projectService.assignProjectToPlayer(player, project, gameTick);

                        // Notify all players about the (now unavailable) tender
                        GameEvent<Project> projectUpdatedEvent = new GameEvent<>(EventType.PROJECT_UPDATED);
                        projectUpdatedEvent.setPayload(project);
                        messagingService.broadcast(projectUpdatedEvent);

                        // Notify player about the new project
                        GameEvent<Project> projectReceivedEvent = new GameEvent<>(EventType.PROJECT_RECEIVED);
                        projectReceivedEvent.setPayload(project);
                        messagingService.sendToPlayer(player, projectReceivedEvent);
                    } else {
                        GameEvent<Project> tenderUpdatedEvent = new GameEvent<>(EventType.PROJECT_UPDATED);
                        tenderUpdatedEvent.setPayload(project);
                        messagingService.broadcast(tenderUpdatedEvent);
                    }
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Invalid message");
        }
    }

    private void handleAssignEmployeeEvent(WebSocket websocket, String message) {
        Type payloadType = new TypeToken<GameEvent<HashMap<String, Integer>>>() {}.getType();
        GameEvent<HashMap<String, Integer>> assignmentEvent = GameServer.getGson().fromJson(message, payloadType);

        int employeeId = assignmentEvent.getPayload().get(EMPLOYEE_ID);
        int projectId = assignmentEvent.getPayload().get(PROJECT_ID);

        changeEmployeeAssignment(websocket, employeeId, projectId, true);
    }

    private void handleUnassignEmployeeEvent(WebSocket websocket, String message) {
        int employeeId = parseIdByKey(message, EMPLOYEE_ID);
        int projectId = parseIdByKey(message, PROJECT_ID);
        if (employeeId == 0 || projectId == 0) return;

        changeEmployeeAssignment(websocket, employeeId, projectId, false);
    }

    private void handleSkillUnlockedEvent(WebSocket websocket, String message) {
        Player player = playerService.getPlayer(websocket);

        Type payloadType = new TypeToken<GameEvent<HashMap<String, String>>>() {}.getType();
        GameEvent<HashMap<String, String>> skillUnlockedEvent = GameServer.getGson().fromJson(message, payloadType);
        String skillId = skillUnlockedEvent.getPayload().get("skillId");
        int unlockSkillPoints = Integer.parseInt(skillUnlockedEvent.getPayload().get("unlockSkillPoints"));

        skillService.unlockSkill(player, skillId, unlockSkillPoints);
    }

    private void handleHireTalentEvent(WebSocket websocket, String message) {
        Player player = playerService.getPlayer(websocket);
        int employeeId = parseIdByKey(message, EMPLOYEE_ID);

        Employee employee = talentMarket.hireTalent(player, employeeId, gameLifeCycleService.getTick());

        if (employee == null) {
            log.warn("Could not hire talent. Employee {} not found.", employeeId);
            return;
        }

        log.debug("Player {} hired employee {} - {}", player.getId(), employee.getId(), employee.getName());
        log.debug("Talent market has the following employees left: {}", talentMarket.getTalents().size());

        if (skillService.playerHasSkill(player, TEAM_SPIRIT)) {
            employee.addComplexStatusEffect(TEAM_SPIRIT);
        }

        GameEvent<Player> playerUpdateEvent = new GameEvent<>(EventType.STATE_UPDATED);
        playerUpdateEvent.setPayload(player);
        messagingService.sendToPlayer(player, playerUpdateEvent);

        GameEvent<ArrayList<Integer>> employeeHiredEvent = new GameEvent<>(EventType.TALENTS_REMOVED);
        ArrayList<Integer> employeeList = new ArrayList<>();
        employeeList.add(employee.getId());
        employeeHiredEvent.setPayload(employeeList);
        messagingService.broadcast(employeeHiredEvent);
    }

    private void handleRiskAssessmentRequestedEvent(WebSocket websocket, String message) {
        int projectId = parseIdByKey(message, PROJECT_ID);
        Player player = playerService.getPlayer(websocket);
        Project project = projectService.getProjectById(projectId);

        if (project == null) {
            log.warn("Could not assess risk. Project {} not found.", projectId);
            return;
        }

        projectService.assessProjectRiskForPlayer(project, player, gameLifeCycleService.getTick(),
                gameLifeCycleService.getLevel());

        // Send confirmation message to player
        GameEvent<Project> projectUpdatedEvent = new GameEvent<>(EventType.RISK_ASSESSMENT_CONFIRMED);
        projectUpdatedEvent.setPayload(project);
        messagingService.sendToPlayer(player, projectUpdatedEvent);
    }

    private void handleEmployeeDismissedEvent(WebSocket websocket, String message) {
        int employeeId = parseIdByKey(message, EMPLOYEE_ID);
        Player player = playerService.getPlayer(websocket);
        Employee employee = player.getEmployeeById(employeeId);

        if (employee == null) {
            log.warn("Could not dismiss employee. Employee {} not found.", employeeId);
            return;
        }

        // Remove employee from player and all the players' projects
        employeeService.dismissEmployee(player, employee);

        // Move employee back to talent market
        talentMarket.addTalent(employee);
    }

    private void handleProjectStartedEvent(String message) {
        int projectId = parseIdByKey(message, PROJECT_ID);
        int startedAt = parseIdByKey(message, STARTED_AT);
        if (projectId == 0 || startedAt == 0) return;

        Project project = projectService.getProjectById(projectId);
        projectService.startProject(project, startedAt);
    }

    private void handleEmployeeSalaryUpdatedEvent(WebSocket websocket, String message) {
        Player player = playerService.getPlayer(websocket);

        Type payloadType = new TypeToken<GameEvent<HashMap<String, Integer>>>() {}.getType();
        GameEvent<HashMap<String, Integer>> employeeUpdatedEvent = GameServer.getGson().fromJson(message, payloadType);

        int employeeId = employeeUpdatedEvent.getPayload().get(EMPLOYEE_ID);
        Employee employee = player.getEmployeeById(employeeId);
        if (employee == null) {
            log.warn("Could not update employee. Employee {} not found.", employeeId);
            return;
        }

        int salary = employeeUpdatedEvent.getPayload().get("salary");
        employee.setSalary(salary, gameLifeCycleService.getTick());

        messagingService.sendEmployeeUpdate(player, employee);
    }

    private void handleEffectEnabledEvent(WebSocket websocket, String message) {
        Type payloadType = new TypeToken<GameEvent<HashMap<String, String>>>() {}.getType();
        GameEvent<HashMap<String, String>> effectEnabledEvent = GameServer.getGson().fromJson(message, payloadType);

        String effect = effectEnabledEvent.getPayload().get("effect");

        if (effect.equals(CRUNCH_MODE)) {
            int projectId = Integer.parseInt(effectEnabledEvent.getPayload().get(PROJECT_ID));
            Project project = projectService.getProjectById(projectId);

            if (project == null) {
                log.warn("Could not apply effect. Project {} not found.", projectId);
                return;
            }

            for (Employee employee : projectEmployeeService.getEmployeesByProject(project)) {
                employee.addComplexStatusEffect(effect);
                messagingService.sendEmployeeUpdate(playerService.getPlayer(websocket), employee);
            }

            int crunchModeCooldown = 20;
            scheduler.schedule(() -> {
                // Disable effect after cooldown
                for (Employee employee : projectEmployeeService.getEmployeesByProject(project)) {
                    employee.removeStatusEffectsByReason(CRUNCH_MODE);
                    messagingService.sendEmployeeUpdate(playerService.getPlayer(websocket), employee);
                }

                // Notify player
                GameEvent<Map<String, String>> effectDisabledEvent = new GameEvent<>(EventType.EFFECT_DISABLED);
                effectDisabledEvent.setPayload(Map.of("effect", CRUNCH_MODE, PROJECT_ID, String.valueOf(projectId)));
                messagingService.sendToPlayer(playerService.getPlayer(websocket), effectDisabledEvent);
            }, gameLifeCycleService.getGameSpeedInMilliseconds() * (long) crunchModeCooldown, TimeUnit.MILLISECONDS);
        } else if (effect.equals(TEAM_SPIRIT)) {
            for (Employee employee : playerService.getPlayer(websocket).getEmployees()) {
                employee.addComplexStatusEffect(effect);
                messagingService.sendEmployeeUpdate(playerService.getPlayer(websocket), employee);
            }
        }
    }

    private void handleProblemSolvedEvent(String message) {
        log.debug("Problem solved: {}", message);
        Type payloadType = new TypeToken<GameEvent<HashMap<String, String>>>() {}.getType();
        GameEvent<HashMap<String, String>> problemSolvedEvent = GameServer.getGson().fromJson(message, payloadType);

        int projectId = Integer.parseInt(problemSolvedEvent.getPayload().get(PROJECT_ID));
        String translationKey = problemSolvedEvent.getPayload().get("translationKey");
        int tick = Integer.parseInt(problemSolvedEvent.getPayload().get("tick"));

        Project project = projectService.getProjectById(projectId);
        if (project == null) {
            log.warn("Could not solve problem. Project {} not found.", projectId);
            return;
        }

        Optional<Problem> problemToSolve = project.getUnsolvedProblems().stream()
                .filter(problem -> problem.getTranslationKey().equals(translationKey))
                .findFirst();

        if (problemToSolve.isPresent()) {
            problemToSolve.get().setSolvedAt(tick);
        } else {
            log.warn("No matching problem with translationKey '{}' found in project '{}'.",
                    translationKey, projectId);
        }
    }

    private void handleOneToOneMeetingEvent(WebSocket websocket, String message) {
        int employeeId = parseIdByKey(message, EMPLOYEE_ID);
        if (employeeId == 0) return;

        Player player = playerService.getPlayer(websocket);
        Employee employee = player.getEmployeeById(employeeId);

        employee.haveOneToOneMeeting();
        messagingService.sendEmployeeUpdate(player, employee);
    }

    private void handleTeamEstimateRequestedEvent(WebSocket websocket, String message) {
        int projectId = parseIdByKey(message, PROJECT_ID);
        Player player = playerService.getPlayer(websocket);
        projectService.conductTeamEstimation(projectId, player, gameLifeCycleService.getTick());
    }

    private void handleProjectCancelRequestedEvent(WebSocket websocket, String message) {
        int projectId = parseIdByKey(message, PROJECT_ID);
        Project project = projectService.getProjectById(projectId);
        Player player = playerService.getPlayer(websocket);

        if (project != null) {
            log.debug("Cancelling project '{}'...", projectId);
            projectService.cancelProject(player, project, PARTY_CONTRACTOR,
                    gameLifeCycleService.getTick(), gameLifeCycleService.getLevel());

            // Just send the project to the player (cancelledAt has been updated)
            GameEvent<Project> projectUpdatedEvent = new GameEvent<>(EventType.PROJECT_UPDATED);
            projectUpdatedEvent.setPayload(project);
            // Notify all involved parties
            project.getInvolvedPlayers()
                    .forEach(p -> messagingService.sendToPlayer(p, projectUpdatedEvent));
        } else {
            log.warn("Could not cancel project. Project {} not found.", projectId);
        }
    }

    // Parse any single id out of a JSON message with a payload field and a named key
    // Return 0 if the message is invalid, the field is not found, or the field is not an integer
    private static int parseIdByKey(String message, String key) {
        try {
            Type payloadType = new TypeToken<GameEvent<HashMap<String, Integer>>>() {}.getType();
            GameEvent<HashMap<String, Integer>> event = GameServer.getGson().fromJson(message, payloadType);
            return event.getPayload().get(key);
        } catch (Exception e) {
            return 0;
        }
    }

    private void changeEmployeeAssignment(WebSocket websocket, int employeeId, int projectId, boolean isAssignOperation) {
        Player player = playerService.getPlayer(websocket);
        Employee employee = player.getEmployeeById(employeeId);
        Project project = projectService.getProjectById(projectId);

        if (isAssignOperation) {
            projectEmployeeService.assignEmployeeToProject(employee, project);
        } else {
            projectEmployeeService.removeEmployeeFromProject(employee, project);
        }

        // Notify frontend about change
        GameEvent<Employee> projectUpdatedEvent = new GameEvent<>(EventType.EMPLOYEE_UPDATED);
        projectUpdatedEvent.setPayload(employee);
        messagingService.broadcast(GameServer.getGson().toJson(projectUpdatedEvent));
    }
}
