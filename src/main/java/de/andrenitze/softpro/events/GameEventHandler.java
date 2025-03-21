package de.andrenitze.softpro.events;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.andrenitze.softpro.*;
import de.andrenitze.softpro.domains.decisions.LevelDecisions;
import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.projects.Problem;
import de.andrenitze.softpro.domains.projects.Project;
import de.andrenitze.softpro.domains.projects.ProjectPartyExclusionStrategy;
import de.andrenitze.softpro.domains.decisions.Decision;
import de.andrenitze.softpro.domains.decisions.DecisionDAO;
import de.andrenitze.softpro.config.DatabaseConfig;
import org.java_websocket.WebSocket;

import java.lang.reflect.Type;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static de.andrenitze.softpro.Game.GAME_SPEED_IN_MILLISECONDS;
import static de.andrenitze.softpro.Main.logger;

public class GameEventHandler {
    public static final String TEAM_SPIRIT = "team-spirit";
    public static final String CRUNCH_MODE = "crunch-mode";
    public static final String PARTY_CONTRACTOR = "contractor";
    public static final String PARTY_CLIENT = "client";
    public static final String EMPLOYEE_ID = "employeeId";
    public static final String PROJECT_ID = "projectId";
    private final Game game;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public GameEventHandler(Game game) {
        this.game = game;
    }

    public void handleEvent(WebSocket websocket, String message) {
        GameEvent<?> event = GameServer.getGson().fromJson(message, GameEvent.class);
        logger.debug("Identified event: {}", event.getType());

        if (event.getType() == null) {
            logger.warn("Received event with unknown type: {}", message);
            return;
        }

        try {
            switch (event.getType()) {
                case JOIN_TENDER -> handleJoinTenderEvent(websocket, message);
                case ASSIGN_EMPLOYEE -> handleAssignEmployeeEvent(websocket, message);
                case PLAYER_READY -> handlePlayerReadyEvent(websocket, message);
                case UNASSIGN_EMPLOYEE -> handleUnassignEmployeeEvent(websocket, message);
                case SKILL_UNLOCKED -> handleSkillUnlockedEvent(websocket, message);
                case HIRE_TALENT -> handleHireTalentEvent(websocket, message);
                case RISK_ASSESSMENT_REQUESTED -> handleRiskAssessmentRequestedEvent(websocket, message);
                case EMPLOYEE_DISMISSED -> handleEmployeeDismissedEvent(websocket, message);
                case PROJECT_STARTED -> handleProjectStartedEvent(message);
                case EMPLOYEE_SALARY_UPDATED -> handleEmployeeSalaryUpdatedEvent(websocket, message);
                case EFFECT_ENABLED -> handleEffectEnabledEvent(websocket, message);
                case PAUSE -> game.pause();
                case RESUME -> game.resume();
                case PROBLEM_SOLVED -> handleProblemSolvedEvent(message);
                case ONE_TO_ONE_MEETING -> handleOneToOneMeetingEvent(websocket, message);
                case TEAM_ESTIMATE_REQUESTED -> handleTeamEstimateRequestedEvent(websocket, message);
                case PROJECT_CANCEL_REQUESTED -> handleProjectCancelRequestedEvent(websocket, message);
                default -> logger.warn("Received unknown event type: {}", event.getType());
            }
        } catch (Exception e) {
            logger.error("Error handling event: {}", e.getMessage());
        }
    }

    private void handleJoinTenderEvent(WebSocket websocket, String message) {
        Type payloadType = new TypeToken<GameEvent<Integer>>() {}.getType();
        GameEvent<Integer> joinTenderEvent = GameServer.getGson().fromJson(message, payloadType);

        try {
            for (Project project : game.getProjectService().getProjects()) {
                if (project.getId() == joinTenderEvent.getPayload()) {
                    Player player = game.getPlayerByWebSocket(websocket);
                    project.addParty(player);

                    if (project.hasNoTenderProcess()) {
                        game.assignProjectToPlayer(player, project);
                    } else {
                        Gson gson = new GsonBuilder()
                                .setExclusionStrategies(new ProjectPartyExclusionStrategy())
                                .create();
                        GameEvent<Project> tenderUpdatedEvent = new GameEvent<>(EventType.PROJECT_UPDATED);
                        tenderUpdatedEvent.setPayload(project);
                        game.getMessagingService().broadcastToAllPlayers(gson.toJson(tenderUpdatedEvent));
                    }
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("Invalid message");
        }
    }

    private void handleAssignEmployeeEvent(WebSocket websocket, String message) {
        Type payloadType = new TypeToken<GameEvent<HashMap<String, Integer>>>() {}.getType();
        GameEvent<HashMap<String, Integer>> assignmentEvent = GameServer.getGson().fromJson(message, payloadType);

        int employeeId = assignmentEvent.getPayload().get(EMPLOYEE_ID);
        int projectId = assignmentEvent.getPayload().get(PROJECT_ID);

        changeEmployeeAssignment(websocket, employeeId, projectId, true);
    }

    private void handlePlayerReadyEvent(WebSocket websocket, String message) {
        Player player = game.getPlayerByWebSocket(websocket);
        Type payloadType = new TypeToken<GameEvent<LevelDecisions>>() {}.getType();
        GameEvent<LevelDecisions> playerReadyEvent = GameServer.getGson().fromJson(message, payloadType);

        int level = playerReadyEvent.getPayload().level();
        List<Decision> decisions = playerReadyEvent.getPayload().decisions();

        player.setDecisions(level, decisions);
        logger.debug("Saved {} player decision(s) for level {}.",
                player.getDecisionsByLevel(level).size(), level);

        DecisionDAO decisionDao = new DecisionDAO(DatabaseConfig.getDataSource());
        try {
            decisionDao.saveDecisions(player.getId().toString(), level, decisions);
        } catch (SQLException e) {
            logger.error("Could not persist player decisions to database: {}", e.getMessage());
        }

        game.prepareNextLevel();
    }

    private void handleUnassignEmployeeEvent(WebSocket websocket, String message) {
        int employeeId = parseIdByKey(message, EMPLOYEE_ID);
        int projectId = parseIdByKey(message, PROJECT_ID);
        if (employeeId == 0 || projectId == 0) return;

        changeEmployeeAssignment(websocket, employeeId, projectId, false);
    }

    private void handleSkillUnlockedEvent(WebSocket websocket, String message) {
        Player player = game.getPlayerByWebSocket(websocket);

        Type payloadType = new TypeToken<GameEvent<HashMap<String, String>>>() {}.getType();
        GameEvent<HashMap<String, String>> skillUnlockedEvent = GameServer.getGson().fromJson(message, payloadType);
        String skillId = skillUnlockedEvent.getPayload().get("skillId");
        int unlockSkillPoints = Integer.parseInt(skillUnlockedEvent.getPayload().get("unlockSkillPoints"));

        game.getSkillsManager().unlockSkill(player, skillId, unlockSkillPoints);
    }

    private void handleHireTalentEvent(WebSocket websocket, String message) {
        Type payloadType = new TypeToken<GameEvent<Integer>>() {}.getType();
        GameEvent<Integer> hireTalentEvent = GameServer.getGson().fromJson(message, payloadType);

        Player player = game.getPlayerByWebSocket(websocket);
        int employeeId = hireTalentEvent.getPayload();

        Employee employee = game.getTalentMarket().hireTalent(player, employeeId, game.getCurrentTick());

        if (employee == null) {
            logger.warn("Could not hire talent. Employee {} not found.", employeeId);
            return;
        }

        logger.debug("Player {} hired employee {} - {}", player.getName(), employee.getId(), employee.getName());
        logger.debug("Talent market has the following employees left: {}", game.getTalentMarket().getTalents().size());

        SkillsManager skillsManager = game.getSkillsManager();
        if (skillsManager.playerHasSkill(player, TEAM_SPIRIT)) {
            employee.addComplexStatusEffect(TEAM_SPIRIT);
        }

        GameEvent<Player> playerUpdateEvent = new GameEvent<>(EventType.STATE_UPDATED);
        playerUpdateEvent.setPayload(player);
        game.getMessagingService().sendMessageToPlayer(player, GameServer.getGson().toJson(playerUpdateEvent));

        GameEvent<ArrayList<Integer>> employeeHiredEvent = new GameEvent<>(EventType.TALENTS_REMOVED);
        ArrayList<Integer> employeeList = new ArrayList<>();
        employeeList.add(employee.getId());
        employeeHiredEvent.setPayload(employeeList);
        game.getMessagingService().broadcastToAllPlayers(GameServer.getGson().toJson(employeeHiredEvent));
    }

    private void handleRiskAssessmentRequestedEvent(WebSocket websocket, String message) {
        Type payloadType = new TypeToken<GameEvent<HashMap<String, Integer>>>() {}.getType();
        GameEvent<HashMap<String, Integer>> riskAssessmentEvent = GameServer.getGson().fromJson(message, payloadType);

        int projectId = riskAssessmentEvent.getPayload().get(PROJECT_ID);
        Player player = game.getPlayerByWebSocket(websocket);

        game.assessProjectRiskForPlayer(projectId, player);
    }

    private void handleEmployeeDismissedEvent(WebSocket websocket, String message) {
        Type payloadType = new TypeToken<GameEvent<HashMap<String, Integer>>>() {}.getType();
        GameEvent<HashMap<String, Integer>> dismissEmployeeEvent = GameServer.getGson().fromJson(message, payloadType);
        int employeeId = dismissEmployeeEvent.getPayload().get(EMPLOYEE_ID);

        Player player = game.getPlayerByWebSocket(websocket);
        Employee employee = player.getEmployeeById(employeeId);

        if (employee == null) {
            logger.warn("Could not dismiss employee. Employee {} not found.", employeeId);
            return;
        }

        employee.removeAllStatusEffects();
        game.dismissEmployee(player, employee);
    }

    private void handleProjectStartedEvent(String message) {
        Type payloadType = new TypeToken<GameEvent<HashMap<String, Integer>>>() {}.getType();
        GameEvent<HashMap<String, Integer>> projectStartedEvent = GameServer.getGson().fromJson(message, payloadType);

        int projectId = projectStartedEvent.getPayload().get(PROJECT_ID);
        int startedAt = projectStartedEvent.getPayload().get("startedAt");

        Project project = game.getProjectService().getProjectById(projectId);
        game.getProjectService().startProject(project, startedAt);
    }

    private void handleEmployeeSalaryUpdatedEvent(WebSocket websocket, String message) {
        Player player = game.getPlayerByWebSocket(websocket);

        Type payloadType = new TypeToken<GameEvent<HashMap<String, Integer>>>() {}.getType();
        GameEvent<HashMap<String, Integer>> employeeUpdatedEvent = GameServer.getGson().fromJson(message, payloadType);

        int employeeId = employeeUpdatedEvent.getPayload().get(EMPLOYEE_ID);
        Employee employee = player.getEmployeeById(employeeId);
        if (employee == null) {
            logger.warn("Could not update employee. Employee {} not found.", employeeId);
            return;
        }

        int salary = employeeUpdatedEvent.getPayload().get("salary");
        employee.setSalary(salary, game.getCurrentTick());

        game.getMessagingService().sendEmployeeUpdate(player, employee);
    }

    private void handleEffectEnabledEvent(WebSocket websocket, String message) {
        Type payloadType = new TypeToken<GameEvent<HashMap<String, String>>>() {}.getType();
        GameEvent<HashMap<String, String>> effectEnabledEvent = GameServer.getGson().fromJson(message, payloadType);

        String effect = effectEnabledEvent.getPayload().get("effect");

        if (effect.equals(CRUNCH_MODE)) {
            int projectId = Integer.parseInt(effectEnabledEvent.getPayload().get(PROJECT_ID));
            Project project = game.getProjectService().getProjectById(projectId);

            if (project == null) {
                logger.warn("Could not apply effect. Project {} not found.", projectId);
                return;
            }

            for (Employee employee : game.getProjectService().getProjectEmployeesMap().get(project)) {
                employee.addComplexStatusEffect(effect);
                game.getMessagingService().sendEmployeeUpdate(game.getPlayerByWebSocket(websocket), employee);
            }

            int crunchModeCooldown = 20;
            scheduler.schedule(() -> {
                GameEvent<Map<String, String>> effectDisabledEvent = new GameEvent<>(EventType.EFFECT_DISABLED);
                effectDisabledEvent.setPayload(Map.of("effect", CRUNCH_MODE));
                game.getMessagingService().sendMessageToPlayer(game.getPlayerByWebSocket(websocket), GameServer.getGson().toJson(effectDisabledEvent));
            }, GAME_SPEED_IN_MILLISECONDS * (long) crunchModeCooldown, TimeUnit.MILLISECONDS);
        } else if (effect.equals(TEAM_SPIRIT)) {
            for (Employee employee : game.getPlayerByWebSocket(websocket).getEmployees()) {
                employee.addComplexStatusEffect(effect);
                game.getMessagingService().sendEmployeeUpdate(game.getPlayerByWebSocket(websocket), employee);
            }
        }
    }

    private void handleProblemSolvedEvent(String message) {
        logger.debug("Problem solved: {}", message);
        Type payloadType = new TypeToken<GameEvent<HashMap<String, String>>>() {}.getType();
        GameEvent<HashMap<String, String>> problemSolvedEvent = GameServer.getGson().fromJson(message, payloadType);

        int projectId = Integer.parseInt(problemSolvedEvent.getPayload().get(PROJECT_ID));
        String translationKey = problemSolvedEvent.getPayload().get("translationKey");
        int tick = Integer.parseInt(problemSolvedEvent.getPayload().get("tick"));

        Project project = game.getProjectService().getProjectById(projectId);
        if (project == null) {
            logger.warn("Could not solve problem. Project {} not found.", projectId);
            return;
        }

        Optional<Problem> problemToSolve = project.getUnsolvedProblems().stream()
                .filter(problem -> problem.getTranslationKey().equals(translationKey))
                .findFirst();

        if (problemToSolve.isPresent()) {
            problemToSolve.get().setSolvedAt(tick);
        } else {
            logger.warn("No matching problem with translationKey '{}' found in project '{}'.",
                    translationKey, projectId);
        }
    }

    private void handleOneToOneMeetingEvent(WebSocket websocket, String message) {
        int employeeId = parseIdByKey(message, EMPLOYEE_ID);
        if (employeeId == 0) return;

        Player player = game.getPlayerByWebSocket(websocket);
        Employee employee = player.getEmployeeById(employeeId);

        game.conductOneToOneMeeting(player, employee);
    }

    private void handleTeamEstimateRequestedEvent(WebSocket websocket, String message) {
        int projectId = parseIdByKey(message, PROJECT_ID);
        Player player = game.getPlayerByWebSocket(websocket);
        game.getProjectService().conductTeamEstimation(projectId, player);
    }

    private void handleProjectCancelRequestedEvent(WebSocket websocket, String message) {
        int projectId = parseIdByKey(message, PROJECT_ID);
        Project project = game.getProjectService().getProjectById(projectId);
        Player player = game.getPlayerByWebSocket(websocket);

        if (project != null) {
            logger.debug("Cancelling project '{}'...", projectId);
            game.getProjectService().cancelProject(player, project, PARTY_CONTRACTOR);
        } else {
            logger.warn("Could not cancel project. Project {} not found.", projectId);
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
        Player player = game.getPlayerByWebSocket(websocket);
        Employee employee = player.getEmployeeById(employeeId);
        Project project = game.getProjectService().getProjectById(projectId);

        if (isAssignOperation) {
            game.getProjectService().assignEmployeeToProject(employee, project);
        } else {
            game.getProjectService().removeEmployeeFromProject(employee, project);
        }

        // Notify frontend about change
        GameEvent<Project> projectUpdatedEvent = new GameEvent<>(EventType.EMPLOYEE_UPDATED);
        projectUpdatedEvent.setPayload(project);
        game.getMessagingService().broadcastToAllPlayers(GameServer.getGson().toJson(projectUpdatedEvent));
    }
}
