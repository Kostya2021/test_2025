package de.andrenitze.softpro;

import com.google.gson.Gson;
import de.andrenitze.softpro.events.EventType;
import de.andrenitze.softpro.events.GameEvent;
import org.java_websocket.WebSocket;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.exp;

public class Game {
    private static final int GAME_SPEED_IN_MILLISECONDS = 100;
    private static final int BANKRUPTCY_THRESHOLD = -10000;
    private static final String EVENT_TYPE = "type";
    private static final String EARNED_VALUE = "earnedValue";
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private GameServer gameServer;
    private final ConcurrentHashMap<WebSocket, Player> players;
    private final ArrayList<Project> projects;
    private int currentTick;
    private Date currentDate;
    private final ScheduledExecutorService gameLoop;
    private GameEventHandler eventHandler;
    private final ConcurrentHashMap<Project, ArrayList<Employee>> projectEmployeesMap;
    private static final Gson GSON = new Gson();

    // Every GameServer hosts exactly one Game
    Game(ConcurrentHashMap<WebSocket, Player> players, GameServer gameServer) {
        // Every game consists of players and a world in a specific state
        this.players = players;
        this.gameServer = gameServer;
        this.eventHandler = new GameEventHandler(this);
        currentTick = 0;
        currentDate = new Date();
        projects = new ArrayList<>();
        projectEmployeesMap = new ConcurrentHashMap<>();
        logger.debug("A new game has started with {} players.", players.size());

        // Send initial state to all players
        players.forEach((webSocket, player) -> {
            // Funds
            sendFundsUpdateToPlayer(player);

            // Employees
            JSONObject gameObjectWrapper = new JSONObject();
            JSONObject initialState = new JSONObject();
            
            JSONArray employeesArray = new JSONArray();
            for (Employee employee : player.getEmployees()) {
                JSONObject employeeObject = new JSONObject();
                employeeObject.put("id", employee.getId());
                employeeObject.put("name", employee.getName());
                employeeObject.put("age", employee.getAge());
                employeeObject.put("salary", employee.getSalary());
                employeeObject.put("experience", employee.getExperienceInDays());
                employeesArray.put(employeeObject);
            }

            initialState.put("employees", employeesArray);
            gameObjectWrapper.put(EVENT_TYPE, "UPDATE_STATE");
            gameObjectWrapper.put("game", initialState);
            logger.debug("Sending initial state to players");
            sendMessageToAllPlayers(gameObjectWrapper.toString());
        });

        // Start running the game time
        gameLoop = Executors.newSingleThreadScheduledExecutor();
        gameLoop.scheduleAtFixedRate(() -> {
            // Notify all clients of current time
            this.sendMessageToAllPlayers("{ \""+EVENT_TYPE+"\": \"T\", \"t\": " + getCurrentTick() + "}");

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

        long startTime = System.nanoTime();
        // Execute these things each "tick" (naming convention: methodNamePerTick)
        // This is important because player interactions alter the state between ticks
        conductWorkOnAllProjectsPerTick();
        processSalariesAndAdjustFundsPerTick(c);
        checkGameOverConditionsAndKickPlayersPerTick();
        randomlySpawnProjectTendersPerTick();
        assignProjectsPerTick();
        long endTime = System.nanoTime();
        long timeElapsedInMilliseconds = (endTime - startTime) / 1000000;

        if (timeElapsedInMilliseconds >= 1) {
            logger.debug("Execution time of game loop: {} ms", timeElapsedInMilliseconds);
        }

    }

    private void processSalariesAndAdjustFundsPerTick(Calendar c) {
        if (isFirstDayOfMonth(c)) {
            players.forEach((webSocket, player) -> {
                player.calculateAndSubtractSalaries();
                sendFundsUpdateToPlayer(player);
            });
        }
    }

    private void sendFundsUpdateToPlayer(Player player) {
        JSONObject newStateEvent = new JSONObject();
        newStateEvent.put(EVENT_TYPE, "NEW_FUNDS");
        newStateEvent.put("funds", player.getFunds());
        sendMessageToPlayer(player, newStateEvent.toString());
    }

    private void checkGameOverConditionsAndKickPlayersPerTick() {
        players.forEach((webSocket, player) -> {
            if (player.getFunds() <= BANKRUPTCY_THRESHOLD) {
                GameEvent<HashMap<String, Integer>> gameOverEvent = new GameEvent<>();
                gameOverEvent.setType(EventType.GAME_OVER);

                int deliveredProjects = 0;
                int projectsVolume = 0;
                HashMap<String, Integer> stats = new HashMap<>();
                for (Project project: this.getProjects()) {
                    if (project.isCompleted() && project.playerWasInvolved(player)) {
                        deliveredProjects++;
                        projectsVolume += project.getTotalValue();
                    }
                }

                stats.put("deliveredProjects", deliveredProjects);
                stats.put("projectsVolume", projectsVolume);
                gameOverEvent.setPayload(stats);

                sendMessageToPlayer(player, GSON.toJson(gameOverEvent));

                // Tell Gameserver to move player back to lobby
                gameServer.addPlayer(webSocket, player);

                kickPlayer(webSocket);
            }
        });
    }

    private void randomlySpawnProjectTendersPerTick() {
        if (new Random().nextFloat() >= 0.95) {
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
            newTenderJson.put("riskLevel", project.getRiskLevel());
            newTenderJson.put(EARNED_VALUE, project.getEarnedValue());
            newTenderJson.put("timeLeftForTender", project.getTenderDeadlineInDays());
            newTenderEvent.put("tender", newTenderJson);

            sendMessageToAllPlayers(newTenderEvent.toString());
        }
    }

    private void assignProjectsPerTick() {
        for (Project project : projects) {
            if (project.getTenderDeadlineInDays() == 0) {
                // Set deadline to -1 to exclude it from further evaluations
                project.setTenderDeadlineInDays(-1);

                // Decide who gets the project
                if (project.getInvolvedPlayers().size() == 1) {
                    logger.debug("Found project {} with a deadline", project.getName());
                    // Serialize project as JSONObject
                    JSONObject projectObject = new JSONObject();
                    projectObject.put("id", project.getId());
                    projectObject.put("name", project.getName());
                    projectObject.put("totalValue", project.getTotalValue());
                    projectObject.put(EARNED_VALUE, project.getEarnedValue());
                    projectObject.put("riskLevel", project.getRiskLevel());

                    // Inform winner with a confirmation message
                    JSONObject wonTenderEvent = new JSONObject();
                    wonTenderEvent.put(EVENT_TYPE, "PROJECT");
                    wonTenderEvent.put("project", projectObject);

                    sendMessageToPlayer(project.getInvolvedPlayers().get(0), wonTenderEvent.toString());
                }
            } else if (project.getTenderDeadlineInDays() != 0 && project.getTenderDeadlineInDays() != -1) {
                // Regular case: Just decrease the time left for tender participation
                project.decreaseTimeLeftForTender();
            }
        }
    }

    public void immediatelyHideAcceptedProject(Project project) {
        if (!project.hasTenderProcess() && project.getInvolvedPlayers().size() == 1) {
            JSONObject closeTenderEvent = new JSONObject();
            closeTenderEvent.put(EVENT_TYPE, "CLOSE_TENDER");
            closeTenderEvent.put("id", project.getId());
            sendMessageToAllPlayers(closeTenderEvent.toString());

            // Make it appear in the next evaluation of assignProjectsPerTick()
            project.setTenderDeadlineInDays(0);
        }
    }

    private void conductWorkOnAllProjectsPerTick() {
        // For all projects that have employees assigned
        Iterator<Map.Entry<Project, ArrayList<Employee>>> iterator = projectEmployeesMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Project, ArrayList<Employee>> entry = iterator.next();
            Project project = entry.getKey();
            ArrayList<Employee> employees = entry.getValue();
            if (employees.isEmpty()) {
                continue;
            }

            addEarnedValueForEachEmployee(project, employees);

            // Finish the project
            if (project.getEarnedValue() >= project.getTotalValue()) {
                // Send reward
                for (Player player : project.getInvolvedPlayers()) {
                    player.addFunds(Math.round(project.getTotalValue() * 0.3));
                    sendFundsUpdateToPlayer(player);
                }

                // Remove the project from employees map, so that employees are unassigned
                project.setEarnedValue(project.getTotalValue());
                iterator.remove();
            }

            // Build event for new project state
            JSONObject projectObject = new JSONObject();
            projectObject.put("id", project.getId());
            projectObject.put(EARNED_VALUE, project.getEarnedValue());

            JSONObject event = new JSONObject();
            event.put(EVENT_TYPE, "PROJECT_UPDATED");
            event.put("project", projectObject);

            // Send update to all involved players
            List<Player> involvedPlayers = project.getInvolvedPlayers();
            for (Player player : involvedPlayers) {
                sendMessageToPlayer(player, event.toString());
            }
        }
    }

    private void addEarnedValueForEachEmployee(Project project, ArrayList<Employee> employees) {
        int earnedValue;
        for (Employee employee : employees) {
            earnedValue = 500;

            // Rule #1: Context changes decrease employee productivity
            int numberOfParallelProjects = getNumberOfParallelProjectsForEmployee(employee);
            earnedValue /= numberOfParallelProjects + 1;
            switch (numberOfParallelProjects) {
                case 1:
                    earnedValue *= 1;
                    break;
                case 2:
                    earnedValue *= 0.4;
                    break;
                case 3:
                    earnedValue *= 0.2;
                    break;
                case 4:
                    earnedValue *= 0.1;
                    break;
                case 5:
                    earnedValue *= 0.05;
                    break;
                default:
                    earnedValue = 1;
                    break;
            }

            // Project experience influences earned value
            // Example:
            // 0 days XP = 0% productivity
            // 1 day XP = 10% productivity
            // 10 days XP = 50% productivity
            // 20 days XP = 100% productivity
            double x = employee.getExperienceInDays(project);
            if (x < 30) {
                double productivityFactor = 1.022595 - 1.02502 * exp(-0.1399307 * x);
                earnedValue = (int) (earnedValue * productivityFactor);
            }

            // Increase the employee's experience
            employee.increaseExperience(project);

            // Increase the project's earnedValue
            project.addEarnedValue(earnedValue);
        }
    }

    private int getNumberOfParallelProjectsForEmployee(Employee employee) {
        int numberOfProjects = 0;

        for (Map.Entry<Project, ArrayList<Employee>> entry : projectEmployeesMap.entrySet()) {
            ArrayList<Employee> employees = entry.getValue();

            if (employees.contains(employee)) {
                numberOfProjects++;
            }
        }
        return numberOfProjects;
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

    void kickPlayer(WebSocket conn) {
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
        try {
            ArrayList<Employee> employees = projectEmployeesMap.get(project);

            if (!employees.contains(employee)) {
                employees.add(employee);
                projectEmployeesMap.put(project, employees);
                logger.debug("{} assigned to {}", employee.getName(), project.getName());
            }
        } catch (NullPointerException e) {
            logger.error(e.toString());
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

    void unassignEmployeeFromProject(Employee employee, Project project) {
        // Get current list of employees working on that project
        ArrayList<Employee> employees = projectEmployeesMap.get(project) ;

        if (employees.contains(employee)) {
            employees.remove(employee);
            projectEmployeesMap.put(project, employees);
            logger.debug("{} unassigned from {}", employee.getName(), project.getName());
        }
    }
}
