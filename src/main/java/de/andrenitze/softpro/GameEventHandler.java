package de.andrenitze.softpro;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import de.andrenitze.softpro.events.GameEvent;
import org.java_websocket.WebSocket;

import java.lang.reflect.Type;
import java.util.HashMap;

class GameEventHandler {
    public static final String EMPLOYEE_ID = "employeeId";
    public static final String PROJECT_ID = "projectId";
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

                            if (project.hasNoTenderProcess()) {
                                game.immediatelyHideAcceptedProject(project);
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

                changeEmployeeAssignment(websocket,
                        assignmentEvent.getPayload().get(EMPLOYEE_ID),
                        assignmentEvent.getPayload().get(PROJECT_ID),
                        true);
                break;
            case UNASSIGN_EMPLOYEE:
                payloadType = new TypeToken<GameEvent<HashMap<String, Integer>>>() {}.getType();
                assignmentEvent = GSON.fromJson(message, payloadType);

                changeEmployeeAssignment(websocket,
                        assignmentEvent.getPayload().get(EMPLOYEE_ID),
                        assignmentEvent.getPayload().get(PROJECT_ID),
                        false);
                break;
            default:
                break;
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
    }
}
