package de.andrenitze.softpro;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.andrenitze.softpro.events.GameEvent;
import de.andrenitze.softpro.serialization.ProjectPartyExclusionStrategy;
import de.andrenitze.softpro.types.EventType;
import org.java_websocket.WebSocket;

import java.lang.reflect.Type;
import java.util.HashMap;

class GameEventHandler {
    private static final Gson GSON = new Gson();
    private final Game game;

    GameEventHandler(Game game) {
        this.game = game;
    }

    void handleEvent(WebSocket websocket, String message) {
        GameEvent<Object> event = GSON.fromJson(message, GameEvent.class);

        switch (event.getType()) {
            case JOIN_TENDER:
                // Fancy way to parse the "tenderId" int out of the message
                Type payloadType = new TypeToken<GameEvent<Integer>>(){}.getType();
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
                break;
            case ASSIGN_EMPLOYEE:
                payloadType = new TypeToken<GameEvent<HashMap<String, Integer>>>(){}.getType();
                GameEvent<HashMap<String, Integer>> assignmentEvent = GSON.fromJson(message, payloadType);

                int employeeId = assignmentEvent.getPayload().get("employeeId");
                int projectId = assignmentEvent.getPayload().get("projectId");

                changeEmployeeAssignment(websocket, employeeId, projectId, true);
                break;
            case UNASSIGN_EMPLOYEE:
                payloadType = new TypeToken<GameEvent<HashMap<String, Integer>>>(){}.getType();
                GameEvent<HashMap<String, Integer>> unassignmentEvent = GSON.fromJson(message, payloadType);

                employeeId = unassignmentEvent.getPayload().get("employeeId");
                projectId = unassignmentEvent.getPayload().get("projectId");

                changeEmployeeAssignment(websocket, employeeId, projectId,false);
                break;
            case SKILL_UNLOCKED:
                Player player = game.getPlayerByWebSocket(websocket);

                payloadType = new TypeToken<GameEvent<HashMap<String, String>>>(){}.getType();
                GameEvent<HashMap<String, String>> skillUnlockedEvent = GSON.fromJson(message, payloadType);
                String skillId = skillUnlockedEvent.getPayload().get("skillId");
                int unlockSkillPoints = Integer.parseInt(skillUnlockedEvent.getPayload().get("unlockSkillPoints"));

                game.getSkillsMananger().unlockSkill(player, skillId, unlockSkillPoints);
                break;
            default:
                break;
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
