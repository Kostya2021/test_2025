package de.andrenitze.softpro;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.andrenitze.softpro.entities.LevelDecisions;
import de.andrenitze.softpro.events.GameEvent;
import de.andrenitze.softpro.serialization.ProjectPartyExclusionStrategy;
import de.andrenitze.softpro.types.Decision;
import de.andrenitze.softpro.types.DecisionDAO;
import de.andrenitze.softpro.types.EventType;
import de.andrenitze.softpro.util.DatabaseConfig;
import org.java_websocket.WebSocket;

import java.lang.reflect.Type;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static de.andrenitze.softpro.Game.GAME_SPEED_IN_MILLISECONDS;
import static de.andrenitze.softpro.Main.logger;

class GameEventHandler {
    private static final Gson GSON = new Gson();
    public static final String TEAM_SPIRIT = "team-spirit";
    public static final String CRUNCH_MODE = "crunch-mode";
    private final Game game;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    GameEventHandler(Game game) {
        this.game = game;
    }

    void handleEvent(WebSocket websocket, String message) {
        GameEvent<?> event = GSON.fromJson(message, GameEvent.class);
        logger.debug("Received event: {}", event.getType());

        // These are messages coming in from the websocket clients (aka the frontend)
        switch (event.getType()) {
            case JOIN_TENDER -> {
                // Fancy way to parse the "tenderId" int out of the message
                Type payloadType = new TypeToken<GameEvent<Integer>>() {}.getType();
                GameEvent<Integer> tenderIdEvent = GSON.fromJson(message, payloadType);

                // A player joins a tender or simply accepts a project
                try {
                    for (Project project : game.getProjects()) {
                        if (project.getId() == tenderIdEvent.getPayload()) {
                            // Assign player to project
                            Player player = game.getPlayerByWebSocket(websocket);
                            project.addParty(player);

                            // Broadcast this player's participation in the tender
                            Gson gson = new GsonBuilder()
                                    .setExclusionStrategies(new ProjectPartyExclusionStrategy())
                                    .create();
                            GameEvent<Project> projectUpdatedEvent = new GameEvent<>(EventType.PROJECT_UPDATED);
                            projectUpdatedEvent.setPayload(project);
                            game.broadcastToAllPlayers(gson.toJson(projectUpdatedEvent));

                            // If there is no tender, just assign it
                            if (project.hasNoTenderProcess()) {
                                game.immediatelyCloseTender(project);
                            }
                            break;
                        }
                    }
                } catch (Exception e) {
                    System.out.println("invalid message");
                }
            }
            case ASSIGN_EMPLOYEE -> {
                Type payloadType = new TypeToken<GameEvent<HashMap<String, Integer>>>() {}.getType();
                GameEvent<HashMap<String, Integer>> assignmentEvent = GSON.fromJson(message, payloadType);

                int employeeId = assignmentEvent.getPayload().get("employeeId");
                int projectId = assignmentEvent.getPayload().get("projectId");

                changeEmployeeAssignment(websocket, employeeId, projectId, true);
            }
            case PLAYER_READY -> {
                // Extract decisions sample and save them to the player object
                Player player = game.getPlayerByWebSocket(websocket);
                Type payloadType = new TypeToken<GameEvent<LevelDecisions>>() {}.getType();
                GameEvent<LevelDecisions> playerReadyEvent = GSON.fromJson(message, payloadType);

                int level = playerReadyEvent.getPayload().level();
                List<Decision> decisions = playerReadyEvent.getPayload().decisions();

                player.setDecisions(level, decisions);
                logger.debug("Saved {} player decision(s) for level {}.",
                        player.getDecisionsByLevel(level).size(), level);

                // Persist player decision(s) to database
                DecisionDAO decisionDao = new DecisionDAO(DatabaseConfig.getDataSource());
                try {
                    decisionDao.saveDecisions(player.getId().toString(), level, decisions);
                } catch (SQLException e) {
                    logger.error("Could not persist player decisions to database: {}", e.getMessage());
                }

                // The Player and Game classes both have a "level" attribute, sp
                // make sure the next level is set in the game instance correctly.
                game.prepareNextLevel();
            }
            case ROUND_STARTED -> {
            }
            case UNASSIGN_EMPLOYEE -> {
                Type payloadType = new TypeToken<GameEvent<HashMap<String, Integer>>>() {}.getType();
                GameEvent<HashMap<String, Integer>> unassignmentEvent = GSON.fromJson(message, payloadType);

                int employeeId = unassignmentEvent.getPayload().get("employeeId");
                int projectId = unassignmentEvent.getPayload().get("projectId");

                changeEmployeeAssignment(websocket, employeeId, projectId, false);
            }
            case SKILL_UNLOCKED -> {
                Player player = game.getPlayerByWebSocket(websocket);

                Type payloadType = new TypeToken<GameEvent<HashMap<String, String>>>() {
                }.getType();
                GameEvent<HashMap<String, String>> skillUnlockedEvent = GSON.fromJson(message, payloadType);
                String skillId = skillUnlockedEvent.getPayload().get("skillId");
                int unlockSkillPoints = Integer.parseInt(skillUnlockedEvent.getPayload().get("unlockSkillPoints"));

                game.getSkillsManager().unlockSkill(player, skillId, unlockSkillPoints);
                game.getSkillsManager().playerHasSkill(player, skillId);
            }
            case HIRE_TALENT -> {
                // Parse the employee id out of the message
                Type payloadType = new TypeToken<GameEvent<Integer>>() {}.getType();
                GameEvent<Integer> hireTalentEvent = GSON.fromJson(message, payloadType);

                Player player = game.getPlayerByWebSocket(websocket);
                int employeeId = hireTalentEvent.getPayload();

                Employee employee = game.getTalentMarket().hireTalent(player, employeeId);

                if (employee == null) {
                    logger.warn("Could not hire talent. Employee {} not found.", employeeId);
                    break;
                }

                logger.debug("Player {} hired employee {} - {}", player.getName(), employee.getId(), employee.getName());
                logger.debug("Talent market has the following employees left: {}", game.getTalentMarket().getTalents().size());

                // Apply "team-spirit" effect if unlocked
                SkillsManager skillsManager = game.getSkillsManager();
                if (skillsManager.playerHasSkill(player, TEAM_SPIRIT)) {
                    employee.addComplexStatusEffect(TEAM_SPIRIT);
                }

                // Notify player about new employee
                GameEvent<Player> playerUpdateEvent = new GameEvent<>(EventType.STATE_UPDATED);
                playerUpdateEvent.setPayload(player);
                game.sendMessageToPlayer(player, GSON.toJson(playerUpdateEvent));

                // Remove employee from talent market, so that other players can't hire the same employee
                GameEvent<ArrayList<Integer>> employeeHiredEvent = new GameEvent<>(EventType.TALENTS_REMOVED);
                ArrayList<Integer> employeeList = new ArrayList<>();
                employeeList.add(employee.getId());
                employeeHiredEvent.setPayload(employeeList);
                game.broadcastToAllPlayers(GSON.toJson(employeeHiredEvent));
            }
            case RISK_ASSESSMENT_REQUESTED -> {
                // Parse the project id out of the message
                Type payloadType = new TypeToken<GameEvent<HashMap<String, Integer>>>() {}.getType();
                GameEvent<HashMap<String, Integer>> riskAssessmentEvent = GSON.fromJson(message, payloadType);

                int projectId = riskAssessmentEvent.getPayload().get("projectId");
                Player player = game.getPlayerByWebSocket(websocket);

                // Confirm the risk assessment for this player *WITHOUT* changing the data model in the Project instance.
                // This is necessary to prevent changing the risk visibility for other players in the game.
                game.assessProjectRiskForPlayer(projectId, player);
            }
            case EMPLOYEE_DISMISSED -> {
                // Parse the employee id out of the message
                Type payloadType = new TypeToken<GameEvent<HashMap<String, Integer>>>() {}.getType();
                GameEvent<HashMap<String, Integer>> dismissEmployeeEvent = GSON.fromJson(message, payloadType);
                int employeeId = dismissEmployeeEvent.getPayload().get("employeeId");

                Player player = game.getPlayerByWebSocket(websocket);
                Employee employee = player.getEmployeeById(employeeId);

                if (employee == null) {
                    logger.warn("Could not dismiss employee. Employee {} not found.", employeeId);
                    break;
                }

                // Remove all status effects from employee
                employee.removeAllStatusEffects();

                game.dismissEmployee(player, employee);
            }
            case GAME_OVER -> {
            }
            case STATE_UPDATED -> {
            }
            case NEW_TENDER -> {
            }
            case NEW_FUNDS -> {
            }
            case PROJECT_RECEIVED -> {
            }
            case TENDER_CLOSED -> {
            }
            case OBJECTIVES_UPDATED -> {
            }
            case PROJECT_UPDATED -> {
            }
            case PROJECT_STARTED -> {
                // Parse project id and startedAt time out of the message, e. g., {projectId: 502, startedAt: 1234567}
                Type payloadType = new TypeToken<GameEvent<HashMap<String, Integer>>>() {}.getType();
                GameEvent<HashMap<String, Integer>> projectStartedEvent = GSON.fromJson(message, payloadType);

                int projectId = projectStartedEvent.getPayload().get("projectId");
                int startedAt = projectStartedEvent.getPayload().get("startedAt");

                Project project = game.getProjectById(projectId);

                game.startProject(project, startedAt);
            }
            case PLAYER_UPDATED -> {
            }
            case NEW_STORY_ELEMENT -> {
            }
            case UPDATE_LOBBY -> {
            }
            case T -> {
            }
            case EMPLOYEE_SALARY_UPDATED -> {
                Player player = game.getPlayerByWebSocket(websocket);

                // Extract employee id and salary fields
                Type payloadType = new TypeToken<GameEvent<HashMap<String, Integer>>>() {}.getType();
                GameEvent<HashMap<String, Integer>> employeeUpdatedEvent = GSON.fromJson(message, payloadType);

                // Extract employee id field and find employee in player's list
                int employeeId = employeeUpdatedEvent.getPayload().get("employeeId");
                Employee employee = player.getEmployeeById(employeeId);
                if (employee == null) {
                    logger.warn("Could not update employee. Employee {} not found.", employeeId);
                    break;
                }

                // Extract salary field and update employee's salary
                int salary = employeeUpdatedEvent.getPayload().get("salary");
                employee.setSalary(salary);

                // Notify the player about employee update
                GameEvent<Employee> employeeUpdateEvent = new GameEvent<>(EventType.EMPLOYEE_UPDATED);
                employeeUpdateEvent.setPayload(employee);
                game.sendMessageToPlayer(player, GSON.toJson(employeeUpdateEvent));
            }
            case EFFECT_ENABLED -> {
                // Extract "projectId" (Integer) and "effect" (String) from payload fields
                Type payloadType = new TypeToken<GameEvent<HashMap<String, String>>>() {}.getType();
                GameEvent<HashMap<String, String>> effectEnabledEvent = GSON.fromJson(message, payloadType);

                String effect = effectEnabledEvent.getPayload().get("effect");

                // Only if effect is crunch mode, get the projectId
                if (effect.equals(CRUNCH_MODE)) {
                    int projectId = Integer.parseInt(effectEnabledEvent.getPayload().get("projectId"));

                    // Apply the effect to the project
                    Project project = game.getProjectById(projectId);

                    // Apply the effect to the employees in the project
                    if (project == null) {
                        logger.warn("Could not apply effect. Project {} not found.", projectId);
                        break;
                    }

                    for (Employee employee : game.projectEmployeesMap.get(project)) {
                        employee.addComplexStatusEffect(effect);
                        GameEvent<Employee> employeeUpdateEvent = new GameEvent<>(EventType.EMPLOYEE_UPDATED);
                        employeeUpdateEvent.setPayload(employee);
                        game.sendMessageToPlayer(game.getPlayerByWebSocket(websocket), GSON.toJson(employeeUpdateEvent));
                    }

                    // Schedule a task to disable "crunch-mode" effect after cooldown (= in-game days)
                    int crunchModeCooldown = 20;
                    scheduler.schedule(() -> {
                        GameEvent<Map<String, String>> effectDisabledEvent = new GameEvent<>(EventType.EFFECT_DISABLED);
                        effectDisabledEvent.setPayload(Map.of("effect", CRUNCH_MODE));
                        game.sendMessageToPlayer(game.getPlayerByWebSocket(websocket), GSON.toJson(effectDisabledEvent));
                    }, GAME_SPEED_IN_MILLISECONDS * crunchModeCooldown, TimeUnit.MILLISECONDS);
                } else if (effect.equals(TEAM_SPIRIT)) {
                    for (Employee employee : game.getPlayerByWebSocket(websocket).getEmployees()) {
                        employee.addComplexStatusEffect(effect);
                        GameEvent<Employee> employeeUpdateEvent = new GameEvent<>(EventType.EMPLOYEE_UPDATED);
                        employeeUpdateEvent.setPayload(employee);
                        game.sendMessageToPlayer(game.getPlayerByWebSocket(websocket), GSON.toJson(employeeUpdateEvent));
                    }
                }
            }
            case EFFECT_DISABLED -> {
            }
            case PLAYER_NAME_UPDATED -> {
            }
            case TENDERS_REMOVED -> {
            }
            case TALENTS_ADDED -> {
            }
            case EMPLOYEE_HIRED -> {
            }
            case TALENTS_REMOVED -> {
            }
            case TENDERS_ADDED -> {
            }
            case VERSION -> {
            }
            case RISK_ASSESSMENT_CONFIRMED -> {
            }
            default -> logger.warn("Received unknown event type: {}", event.getType());
        }
    }

    private void changeEmployeeAssignment(WebSocket websocket, int employeeId, int projectId, boolean isAssignOperation) {
        Player player = game.getPlayerByWebSocket(websocket);
        Employee employee = player.getEmployeeById(employeeId);
        Project project = game.getProjectById(projectId);

        if (isAssignOperation) {
            game.assignEmployeeToProject(employee, project);
        } else {
            game.removeEmployeeFromProject(employee, project);
        }

        // Notify frontend about change
        GameEvent<Project> projectUpdatedEvent = new GameEvent<>(EventType.EMPLOYEE_UPDATED);
        projectUpdatedEvent.setPayload(project);
        game.broadcastToAllPlayers(GSON.toJson(projectUpdatedEvent));
    }
}
