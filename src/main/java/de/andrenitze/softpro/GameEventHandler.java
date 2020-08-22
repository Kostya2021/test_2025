package de.andrenitze.softpro;

import org.java_websocket.WebSocket;
import org.json.JSONObject;

import java.util.Objects;

class GameEventHandler {
    public static final String EVENT_TYPE = "eventType";
    public static final String EMPLOYEE_ID = "employeeId";
    public static final String PROJECT_ID = "projectId";
    private final Game game;

    GameEventHandler(Game game) {
        this.game = game;
    }

    void handleEvent(WebSocket websocket, JSONObject event) {
        switch (event.get(EVENT_TYPE).toString()) {
            case "JOIN_TENDER":
                // A player joins a tender or simply accepts a project
                JSONObject tenderObject = (JSONObject) event.get("tender");
                String projectName = tenderObject.get("name").toString();

                for (Project project : game.getProjects()) {
                    if (project.getName().equals(projectName)) {
                        // Assign player to project
                        Player player = game.getPlayerByWebSocket(websocket);
                        project.addParty(player);

                        if (!project.hasTenderProcess()) {
                            game.immediatelyHideAcceptedProject(project);
                        }
                    }
                }
                break;
            case "ASSIGN_EMPLOYEE":
                if (isValidProjectAssignmentEvent(event)) {
                    changeEmployeeAssignment(websocket,
                            (int) event.get(EMPLOYEE_ID),
                            (int) event.get(PROJECT_ID),
                            true);
                }
                break;
            case "UNASSIGN_EMPLOYEE":
                if (isValidProjectAssignmentEvent(event)) {
                    changeEmployeeAssignment(websocket,
                            (int) event.get(EMPLOYEE_ID),
                            (int) event.get(PROJECT_ID),
                            false);
                }
                break;
            default:
                break;
        }
    }

    private boolean isValidProjectAssignmentEvent(JSONObject event) {
        return !Objects.equals(event.get(EMPLOYEE_ID), "") &&
                !Objects.equals(event.get(PROJECT_ID), "");
    }

    private void changeEmployeeAssignment(WebSocket websocket, int employeeId, int projectId, boolean isAssignOperation) {
        Player player = game.getPlayerByWebSocket(websocket);
        Employee employee = player.getEmployeeById(employeeId);
        Project project = game.getProjectById(projectId);

        if (isAssignOperation) {
            game.assignEmployeeToProject(employee, project);
        } else {
            game.unassignEmployeeFromProject(employee, project);
        }
    }
}
