package de.andrenitze.softpro;

import org.java_websocket.WebSocket;
import org.json.simple.JSONObject;

import static java.lang.Integer.parseInt;

class GameEventHandler {
    private final Game game;

    GameEventHandler(Game game, GameServer gameServer) {
        this.game = game;
    }

    void handleEvent(WebSocket websocket, JSONObject event) {
        switch (event.get("eventType").toString()) {
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
                changeEmployeeAssignment(websocket, event, true);
                break;
            case "UNASSIGN_EMPLOYEE":
                changeEmployeeAssignment(websocket, event, false);
                break;
            default:
                break;
        }
    }

    private void changeEmployeeAssignment(WebSocket websocket, JSONObject event, boolean isAssignOperation) {
        int employeeId = parseInt((String) event.get("employeeId"));
        Player player = game.getPlayerByWebSocket(websocket);
        Employee employee = player.getEmployeeById(employeeId);

        int projectId = parseInt((String) event.get("projectId"));
        Project project = game.getProjectById(projectId);

        if (isAssignOperation) {
            game.assignEmployeeToProject(employee, project);
        } else {
            game.unassignEmployeeFromProject(employee, project);
        }
    }
}
