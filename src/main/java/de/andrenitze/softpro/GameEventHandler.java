package de.andrenitze.softpro;

import org.java_websocket.WebSocket;
import org.json.simple.JSONObject;

import static java.lang.Integer.parseInt;

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
            case "ASSIGN_EMPLOYEE" :
                int employeeId = parseInt((String) event.get("employeeId"));
                Player player = game.getPlayerByWebSocket(websocket);
                Employee employee = player.getEmployeeById(employeeId);

                // Assign to no project
                if (!event.containsKey("projectId")) {
                    // Remove employee from all projects
                    game.unassignEmployeeFromAllProjects(employee);
                    break;
                }

                int projectId = parseInt((String) event.get("projectId"));
                Project project = game.getProjectById(projectId);
                game.assignEmployeeToProject(employee, project);
                break;
            default:
                break;
        }
    }
}
