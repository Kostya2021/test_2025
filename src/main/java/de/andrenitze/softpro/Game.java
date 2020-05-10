package de.andrenitze.softpro;

import org.java_websocket.WebSocket;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Game {
    private static final int GAME_SPEED_IN_MILLISECONDS = 500;
    private static final int BANKRUPTCY_THRESHOLD = -50000;
    public static final String EVENT_TYPE = "eventType";
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private GameServer gameServer;
    private final Map<WebSocket, Player> players;
    private final ArrayList<Project> projects;
    private int currentTick;
    private Date currentDate;
    private final ScheduledExecutorService gameLoop;
    private GameEventHandler eventHandler;
    private final ConcurrentHashMap<Project, ArrayList<Employee>> projectEmployeesMap;

        // Every GameServer hosts exactly one Game
    Game(Map<WebSocket, Player> players, GameServer gameServer) {
        // Every game consists of players and a world in a specific state
        this.players = players;
        this.gameServer = gameServer;
        this.eventHandler = new GameEventHandler(this, gameServer);
        currentTick = 0;
        currentDate = new Date();
        projects = new ArrayList<>();
        projectEmployeesMap = new ConcurrentHashMap<>();
        logger.debug("A new game has started with {} players.", players.size());

        // Send initial state to all players
        players.forEach((webSocket, player) -> {
            JSONObject initialState = new JSONObject();
            initialState.put(EVENT_TYPE, "STATE_UPDATE");

            JSONArray employeesArray = new JSONArray();
            for (Employee employee : player.getEmployees()) {
                JSONObject employeeObject = new JSONObject();
                employeeObject.put("id", employee.getId());
                employeeObject.put("name", employee.getName());
                employeeObject.put("age", employee.getAge());
                employeeObject.put("salary", employee.getSalary());
                employeeObject.put("experience", employee.getExperienceInDays());
                employeesArray.add(employeeObject);
            }

            initialState.put("employees", employeesArray);
            logger.debug("Sending initial state to players");
            logger.debug(initialState.toJSONString());
            sendMessageToAllPlayers(initialState.toJSONString());
        });

        // Start running the game time
        gameLoop = Executors.newSingleThreadScheduledExecutor();
        gameLoop.scheduleAtFixedRate(() -> {
            // Notify all clients of current time
            this.sendMessageToAllPlayers("{\"tick\": " + getCurrentTick() + "}");

            // Progress game time and calculate the world's state for each tick
            progressGameTime();
        }, 0, GAME_SPEED_IN_MILLISECONDS, TimeUnit.MILLISECONDS);
    }

    private void progressGameTime() {
        ++currentTick;

        // Progress calendar date
        Calendar c = Calendar.getInstance();
        c.setTime(currentDate);
        c.add(Calendar.DAY_OF_MONTH, 1);
        currentDate = c.getTime();

        conductWorkOnAllProjects();
        processSalariesAndAdjustFunds(c);
        checkGameOverConditionsAndKickPlayers();
        randomlySpawnProjectTenders();
        decreaseTimeLeftForActiveTenders();
    }

    private void processSalariesAndAdjustFunds(Calendar c) {
        if (isFirstDayOfMonth(c)) {
            logger.debug("Calculating funds for {} players...", players.size());
            players.forEach((webSocket, player) -> {
                player.calculateAndSubtractSalaries();

                // Send the new funds to the player
                JSONObject newStateEvent = new JSONObject();
                newStateEvent.put(EVENT_TYPE, "NEW_FUNDS");
                newStateEvent.put("funds", player.getFunds());
                sendMessageToPlayer(player, newStateEvent.toJSONString());
            });
        }
    }

    private void checkGameOverConditionsAndKickPlayers() {
        players.forEach((webSocket, player) -> {
            if (player.getFunds() <= BANKRUPTCY_THRESHOLD) {
                JSONObject gameOverEvent = new JSONObject();
                gameOverEvent.put(EVENT_TYPE, "GAME_OVER");
                sendMessageToPlayer(player, gameOverEvent.toJSONString());

                // Tell Gameserver to move player back to lobby
                gameServer.addPlayer(webSocket, player);

                // Kick player out of the game
                removePlayer(webSocket);
            }
        });
    }

    private void randomlySpawnProjectTenders() {
        if (new Random().nextFloat() >= 0.8) {
            // Generate a new project
            Project project = Project.generateRandomProject();
            projects.add(project);

            // Initialize project-employee map
            projectEmployeesMap.put(project, new ArrayList<>());

            // Inform players of new project
            JSONObject newTenderEvent = new JSONObject();
            newTenderEvent.put(EVENT_TYPE, "NEW_TENDER");

            // Serialize a project as JSON string
            JSONObject newTenderJson = new JSONObject();
            newTenderJson.put("id", project.getId());
            newTenderJson.put("name", project.getName());
            newTenderJson.put("totalValue", project.getTotalValue());
            newTenderJson.put("earnedValue", project.getEarnedValue());
            newTenderJson.put("timeLeftForTender", project.getTimeLeftForTender());
            newTenderEvent.put("tender", newTenderJson);

            logger.debug("New tender '{}' spawned", project.getName());
            sendMessageToAllPlayers(newTenderEvent.toJSONString());
        }
    }

    private void decreaseTimeLeftForActiveTenders() {
        for (Project project : projects) {
            if (project.getTimeLeftForTender() == 0) {
                // After deadline is exceeded close the call for tender and award the winner
                JSONObject closeTenderEvent = new JSONObject();
                closeTenderEvent.put(EVENT_TYPE, "CLOSE_TENDER");
                closeTenderEvent.put("name", project.getName());
                sendMessageToAllPlayers(closeTenderEvent.toJSONString());
                project.decreaseTimeLeftForTender();

                // Decide who gets the project
                if (project.getInvolvedPlayers().size() == 1) {
                    // Serialize project as JSONObject
                    JSONObject projectObject = new JSONObject();
                    projectObject.put("id", project.getId());
                    projectObject.put("name", project.getName());
                    projectObject.put("totalValue", project.getTotalValue());
                    projectObject.put("earnedValue", project.getEarnedValue());

                    // Inform winner with a confirmation message
                    JSONObject wonTenderEvent = new JSONObject();
                    wonTenderEvent.put(EVENT_TYPE, "PROJECT");
                    wonTenderEvent.put("project", projectObject);

                    sendMessageToPlayer(project.getInvolvedPlayers().get(0), wonTenderEvent.toJSONString());
                }
            } else if (project.getTimeLeftForTender() != 0 && project.getTimeLeftForTender() != -1) {
                project.decreaseTimeLeftForTender();
            }
        }
    }

    private void conductWorkOnAllProjects() {
        // For all projects that have employees assigned
        for (Map.Entry<Project, ArrayList<Employee>> entry : projectEmployeesMap.entrySet()) {
            Project project = entry.getKey();
            ArrayList<Employee> employees = entry.getValue();
            if (!employees.isEmpty()) {
                int earnedValue;

                // Add some value for each employee
                for (Employee employee : employees) {
                    earnedValue = 100;

                    // Increase the employee's experience
                    employee.increaseExperience();

                    // Increase the project's earnedValue
                    project.addEarnedValue(earnedValue);
                }

                // Build event for new project state
                JSONObject projectObject = new JSONObject();
                projectObject.put("id", project.getId());
                projectObject.put("earnedValue", project.getEarnedValue());

                JSONObject event = new JSONObject();
                event.put(EVENT_TYPE, "PROJECT_UPDATE");
                event.put("project", projectObject);

                // Send update to all involved players
                List<Player> players = project.getInvolvedPlayers();
                for (Player player : players) {
                    sendMessageToPlayer(player, event.toJSONString());
                }
            }
        }
    }

    private void sendMessageToAllPlayers(String message) {
        // Send the message to all players
        players.forEach((webSocket, player) -> webSocket.send(message));
    }

    private void sendMessageToPlayer(Player player, String message) {
        // Get the WebSocket connection of the player
        WebSocket webSocket = getWebSocketByPlayer(players, player);

        if (webSocket != null) {
            // Send a single message on that WebSocket connection
            webSocket.send(message);
        }
    }

    private WebSocket getWebSocketByPlayer(Map<WebSocket, Player> map, Player player) {
            return map.keySet()
                    .stream()
                    .filter(key -> player.equals(map.get(key)))
                    .findFirst().orElse(null);
    }

    private boolean isFirstDayOfMonth(Calendar calendar) {
        if (calendar == null) {
            throw new IllegalArgumentException("Calendar cannot be null.");
        }

        int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
        return dayOfMonth == 1;
    }

    private int getCurrentTick() {
        return currentTick;
    }

    void removePlayer(WebSocket conn) {
        players.remove(conn);

        // Close game session if this was the last player
        if (players.size() == 0) {
            shutdown();
        }
    }

    Map<WebSocket, Player> getPlayers() {
        return players;
    }

    void shutdown() {
        gameServer = null;
        gameLoop.shutdownNow();
    }

    boolean hasWebSocket(WebSocket conn) {
        return players.containsKey(conn);
    }

    GameEventHandler getEventHandler() {
        return eventHandler;
    }

    ArrayList<Project> getProjects() {
        return projects;
    }

    Player getPlayerByWebSocket(WebSocket websocket) {
        return players.get(websocket);
    }

    Project getProjectById(int projectId) {
        for (Project project : projects) {
            if (project.getId() == projectId) {
                return project;
            }
        }
        return null;
    }

    void assignEmployeeToProject(Employee employee, Project project) {
        // Get current list of employees working on that project
        ArrayList<Employee> employees = projectEmployeesMap.get(project);

        if (!employees.contains(employee)) {
            employees.add(employee);
            projectEmployeesMap.put(project, employees);
            logger.debug("{} assigned to {}", employee.getName(), project.getName());
        }
    }

    void unassignEmployeeFromAllProjects(Employee employee) {
        for (Project project : projects) {
            ArrayList<Employee> employees = projectEmployeesMap.get(project);
            if (!employees.isEmpty()) {
                for (Employee assignedEmployee : employees) {
                    if (assignedEmployee.equals(employee)) {
                        employees.remove(employee);
                    }
                }
            }
        }
    }
}
