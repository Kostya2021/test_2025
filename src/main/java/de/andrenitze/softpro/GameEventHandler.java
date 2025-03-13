package de.andrenitze.softpro;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.andrenitze.softpro.entities.LevelDecisions;
import de.andrenitze.softpro.entities.Problem;
import de.andrenitze.softpro.entities.GameEvent;
import de.andrenitze.softpro.util.ProjectPartyExclusionStrategy;
import de.andrenitze.softpro.types.Decision;
import de.andrenitze.softpro.types.DecisionDAO;
import de.andrenitze.softpro.types.EventType;
import de.andrenitze.softpro.util.DatabaseConfig;
import org.java_websocket.WebSocket;

import java.lang.reflect.Type;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static de.andrenitze.softpro.Game.GAME_SPEED_IN_MILLISECONDS;
import static de.andrenitze.softpro.GameServer.GSON;
import static de.andrenitze.softpro.Main.logger;

class GameEventHandler {
    public static final String TEAM_SPIRIT = "team-spirit";
    public static final String CRUNCH_MODE = "crunch-mode";
    private final Game game;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    GameEventHandler(Game game) {
        this.game = game;
    }

    void handleEvent(WebSocket websocket, String message) {
        GameEvent<?> event = GSON.fromJson(message, GameEvent.class);
        logger.debug("Identified event: {}", event.getType());

        // These are messages coming in from the websocket clients (aka the frontend)
        if (event.getType() == null) {
            logger.warn("Received event with unknown type: {}", message);
            return;
        }

        switch (event.getType()) {
            case JOIN_TENDER -> {
                // Fancy way to parse the "tenderId" int out of the message
                Type payloadType = new TypeToken<GameEvent<Integer>>() {}.getType();
                GameEvent<Integer> joinTenderEvent = GSON.fromJson(message, payloadType);

                // A player joins a tender or simply accepts a project
                try {
                    for (Project project : game.getProjects()) {
                        if (project.getId() == joinTenderEvent.getPayload()) {
                            // Assign player to project
                            Player player = game.getPlayerByWebSocket(websocket);
                            project.addParty(player);

                            if (project.hasNoTenderProcess()) {
                                // If there is no tender process, just assign the project to the player
                                game.assignProjectToPlayer(player, project);
                            } else {
                                // Tender process: Broadcast this player's participation in the tender
                                Gson gson = new GsonBuilder()
                                        .setExclusionStrategies(new ProjectPartyExclusionStrategy())
                                        .create();
                                GameEvent<Project> tenderUpdatedEvent = new GameEvent<>(EventType.PROJECT_UPDATED);
                                tenderUpdatedEvent.setPayload(project);
                                game.broadcastToAllPlayers(gson.toJson(tenderUpdatedEvent));
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
            case UNASSIGN_EMPLOYEE -> {
                int employeeId = parseIdByKey(message, "employeeId");
                int projectId = parseIdByKey(message, "projectId");
                if (employeeId == 0 || projectId == 0) break;

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
            }
            case HIRE_TALENT -> {
                // Parse the employee id out of the message
                Type payloadType = new TypeToken<GameEvent<Integer>>() {}.getType();
                GameEvent<Integer> hireTalentEvent = GSON.fromJson(message, payloadType);

                Player player = game.getPlayerByWebSocket(websocket);
                int employeeId = hireTalentEvent.getPayload();

                Employee employee = game.getTalentMarket().hireTalent(player, employeeId, game.getCurrentTick());

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
            case PROJECT_STARTED -> {
                // Parse project id and startedAt time out of the message, e. g., {projectId: 502, startedAt: 1234567}
                Type payloadType = new TypeToken<GameEvent<HashMap<String, Integer>>>() {}.getType();
                GameEvent<HashMap<String, Integer>> projectStartedEvent = GSON.fromJson(message, payloadType);

                int projectId = projectStartedEvent.getPayload().get("projectId");
                int startedAt = projectStartedEvent.getPayload().get("startedAt");

                Project project = game.getProjectById(projectId);

                game.startProject(project, startedAt);
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
                employee.setSalary(salary, game.getCurrentTick());

                // Notify the player about employee update
                game.sendEmployeeUpdate(player, employee);
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
                        game.sendEmployeeUpdate(game.getPlayerByWebSocket(websocket), employee);
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
                        game.sendEmployeeUpdate(game.getPlayerByWebSocket(websocket), employee);
                    }
                }
            }
            case PAUSE -> game.pause();
            case RESUME -> game.resume();
            case PROBLEM_SOLVED -> {
                logger.debug("Problem solved: {}", message);
                // Get the project id and the problem's translationKey from the message
                Type payloadType = new TypeToken<GameEvent<HashMap<String, String>>>() {}.getType();
                GameEvent<HashMap<String, String>> problemSolvedEvent = GSON.fromJson(message, payloadType);

                int projectId = Integer.parseInt(problemSolvedEvent.getPayload().get("projectId"));
                String translationKey = problemSolvedEvent.getPayload().get("translationKey");
                int tick = Integer.parseInt(problemSolvedEvent.getPayload().get("tick"));

                Project project = game.getProjectById(projectId);
                if (project == null) {
                    logger.warn("Could not solve problem. Project {} not found.", projectId);
                    break;
                }

                // Find and solve the problem **only in the selected project**
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
            case ONE_TO_ONE_MEETING -> {
                int employeeId = parseIdByKey(message, "employeeId");
                if (employeeId == 0) break;

                Player player = game.getPlayerByWebSocket(websocket);
                Employee employee = player.getEmployeeById(employeeId);

                // Conduct the one-to-one meeting with the employee
                game.conductOneToOneMeeting(player, employee);
            }
            case TEAM_ESTIMATE_REQUESTED -> {
                int projectId = parseIdByKey(message,"projectId");
                Player player = game.getPlayerByWebSocket(websocket);

                // Estimate the project progress
                game.conductTeamEstimation(projectId, player);
            }
            case ROUND_STARTED, STATE_UPDATED, GAME_OVER, NEW_TENDER, NEW_FUNDS, PROJECT_RECEIVED,
                 OBJECTIVES_UPDATED, PROJECT_UPDATED, PLAYER_UPDATED, NEW_STORY_ELEMENT, UPDATE_LOBBY, T,
                 EFFECT_DISABLED, PLAYER_NAME_UPDATED, TENDERS_REMOVED, TALENTS_ADDED, TALENTS_REMOVED, TENDERS_ADDED,
                 VERSION, RISK_ASSESSMENT_CONFIRMED -> {
                // Events not requiring any special handling yet
            }
            default -> logger.warn("Received unknown event type: {}", event.getType());
        }
    }

    // Parse any single id out of a JSON message with a payload field and a named key
    // Return 0 if the message is invalid, the field is not found, or the field is not an integer
    private static int parseIdByKey(String message, String key) {
        try {
            Type payloadType = new TypeToken<GameEvent<HashMap<String, Integer>>>() {}.getType();
            GameEvent<HashMap<String, Integer>> event = GSON.fromJson(message, payloadType);
            return event.getPayload().get(key);
        } catch (Exception e) {
            return 0;
        }
    }

    private void changeEmployeeAssignment(WebSocket websocket, int employeeId, int projectId, boolean isAssignOperation) {
        Player player = game.getPlayerByWebSocket(websocket);
        Employee employee = player.getEmployeeById(employeeId);
        Project project = game.getProjectById(projectId);

        if (isAssignOperation) {
            game.getProjectManager().assignEmployeeToProject(employee, project);
        } else {
            game.getProjectManager().removeEmployeeFromProject(employee, project);
        }

        // Notify frontend about change
        GameEvent<Project> projectUpdatedEvent = new GameEvent<>(EventType.EMPLOYEE_UPDATED);
        projectUpdatedEvent.setPayload(project);
        game.broadcastToAllPlayers(GSON.toJson(projectUpdatedEvent));
    }
}
