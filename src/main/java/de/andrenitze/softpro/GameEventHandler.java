package de.andrenitze.softpro;

import org.java_websocket.WebSocket;
import org.json.simple.JSONObject;

class GameEventHandler {
    private final Game game;
    private final GameServer gameServer;

    GameEventHandler(Game game, GameServer gameServer) {
        this.game = game;
        this.gameServer = gameServer;
    }

    void handleEvent(WebSocket websocket, JSONObject event) {

        switch (event.get("eventType").toString()) {
            case "JOIN_TENDER" :
                // A player joins a tender
                JSONObject tenderObject = (JSONObject) event.get("tender");

                // Find the corresponding project...
                String projectName = tenderObject.get("name").toString();
                for (Project project : game.getProjects()) {
                    if (project.getName().equals(projectName)) {
                        // ...and add the player to the tender process
                        Player player = game.getPlayerByWebSocket(websocket);
                        project.addCompany(player);
                    }
                }
                break;
            case "ASSIGN_EMPLOYEE" :
                JSONObject projectID = (JSONObject) event.get("projectId");
                JSONObject employeeID = (JSONObject) event.get("employeeId");

                int employeeId = Integer.parseInt(employeeID.toString());

                Player player = game.getPlayerByWebSocket(websocket);
                Employee employee = player.getEmployeeById(employeeId);

                // Assign to no project
                if (projectID == null) {
                    // Remove employee from all projects
                    game.unassignEmployeeFromAllProjects(employee);
                    break;
                }

                int projectId = Integer.parseInt(projectID.toString());
                Project project = game.getProjectById(projectId);
                game.assignEmployeeToProject(employee, project);
                break;
            default:
                break;
        }
    }
}
