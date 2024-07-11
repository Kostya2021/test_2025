package de.andrenitze.softpro;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.andrenitze.softpro.events.GameEvent;
import de.andrenitze.softpro.serialization.ProjectPartyExclusionStrategy;
import de.andrenitze.softpro.types.EventType;
import org.java_websocket.WebSocket;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;

import static de.andrenitze.softpro.Main.logger;

class GameEventHandler {
    private static final Gson GSON = new Gson();
    private final Game game;

    GameEventHandler(Game game) {
        this.game = game;
    }

    void handleEvent(WebSocket websocket, String message) {
        GameEvent<Object> event = GSON.fromJson(message, GameEvent.class);
        logger.debug("Received event: " + event.getType());

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

                logger.info("Player {} hired employee {} - {}", player.getName(), employee.getId(), employee.getName());
                logger.info("Talent market has the following employees left: {}", game.getTalentMarket().getTalents().size());

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

                game.dismissEmployee(player, employee);
            }
            default -> logger.warn("Received unknown event type: " + event.getType());
        }
    }

    private boolean changeEmployeeAssignment(WebSocket websocket, int employeeId, int projectId, boolean isAssignOperation) {
        Player player = game.getPlayerByWebSocket(websocket);
        Employee employee = player.getEmployeeById(employeeId);
        Project project = game.getProjectById(projectId);

        if (isAssignOperation) {
            return game.assignEmployeeToProject(employee, project);
        } else {
            return game.removeEmployeeFromProject(employee, project);
        }
    }
}
