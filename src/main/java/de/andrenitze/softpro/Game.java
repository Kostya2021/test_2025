package de.andrenitze.softpro;

import com.google.gson.Gson;
import de.andrenitze.softpro.entities.Objective;
import de.andrenitze.softpro.events.GameEvent;
import de.andrenitze.softpro.types.EventType;
import de.andrenitze.softpro.types.ProjectType;
import org.java_websocket.WebSocket;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.lang.Math.exp;

public class Game {
    private static final int GAME_SPEED_IN_MILLISECONDS = 500;
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
    private final GameEventHandler eventHandler;
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
            logger.debug("Sending initial state to players");
            GameEvent<Player> initialPlayerEvent = new GameEvent<>(EventType.UPDATE_STATE);
            initialPlayerEvent.setPayload(player);
            sendMessageToPlayer(player, GSON.toJson(initialPlayerEvent));
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
        spawnObjectivesPerTick();
        checkObjectivesCriteriaAndSendRewardsPerTick();
        updateEmployeeStatePerTick();

        long endTime = System.nanoTime();
        long timeElapsedInMilliseconds = (endTime - startTime) / 1000000;

        if (timeElapsedInMilliseconds >= 2) {
            logger.debug("Execution time of game loop: {} ms", timeElapsedInMilliseconds);
        }

    }

    private void updateEmployeeStatePerTick() {

    }

    private void checkObjectivesCriteriaAndSendRewardsPerTick() {
        players.forEach((webSocket, player) -> {
            // Calculate progress for all active objectives
            for (Objective objective: player.getObjectives()) {
                // Naive matching approach with exact IDs
                if (objective.getId() == 1 && !objective.isCompleted()) {
                    // Were conditions met (= projects finished) after the objective occurred?
                    // Only check finished projects
                    ArrayList<Project> relevantProjects = (ArrayList<Project>) projects
                            .stream()
                            .filter(project -> project.isCompleted()
                                    && project.getCompletedTick() > objective.getEarliestOccurrence()
                                    && project.playerWasInvolved(player))
                            .collect(Collectors.toList());

                    // The number of relevant projects equals the completed steps
                    objective.setCompletedSteps(relevantProjects.size());

                    GameEvent<List<Objective>> objectivesUpdatedEvent = new GameEvent<>(EventType.UPDATE_OBJECTIVES);
                    List<Objective> allActiveObjectives = player.getActiveObjectivesUntilThisTick(currentTick);
                    objectivesUpdatedEvent.setPayload(allActiveObjectives);
                    sendMessageToPlayer(player, GSON.toJson(objectivesUpdatedEvent));
                }
            }
        });
    }

    private void spawnObjectivesPerTick() {
        players.forEach((webSocket, player) -> {
            List<Objective> newObjectivesInThisTick = player.getNewObjectivesForThisTick(currentTick);
            if (!newObjectivesInThisTick.isEmpty()) {
                GameEvent<List<Objective>> objectivesUpdatedEvent = new GameEvent<>(EventType.UPDATE_OBJECTIVES);
                List<Objective> allActiveObjectives = player.getActiveObjectivesUntilThisTick(currentTick);
                objectivesUpdatedEvent.setPayload(allActiveObjectives);
                sendMessageToPlayer(player, GSON.toJson(objectivesUpdatedEvent));
            }
        });
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
        GameEvent<Double> newFundsEvent = new GameEvent<>(EventType.NEW_FUNDS);
        newFundsEvent.setPayload(player.getFunds());
        String json = GSON.toJson(newFundsEvent);
        sendMessageToPlayer(player, json);
    }

    private void checkGameOverConditionsAndKickPlayersPerTick() {
        players.forEach((webSocket, player) -> {
            if (player.getFunds() <= BANKRUPTCY_THRESHOLD) {
                GameEvent<HashMap<String, Integer>> gameOverEvent = new GameEvent<>();
                gameOverEvent.setEventType(EventType.GAME_OVER);

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

                // Tell game server to move player back to lobby
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
            newTenderEvent.put(EVENT_TYPE, EventType.NEW_TENDER);

            // Serialize a project as JSON string
            newTenderEvent.put("tender", new JSONObject(GSON.toJson(project)));
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
                    wonTenderEvent.put(EVENT_TYPE, EventType.PROJECT);
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
        if (project.hasNoTenderProcess() && project.getInvolvedPlayers().size() == 1) {
            JSONObject closeTenderEvent = new JSONObject();
            closeTenderEvent.put(EVENT_TYPE, EventType.CLOSE_TENDER);
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
            if (project.isCompleted()) {
                // Send reward
                for (Player player : project.getInvolvedPlayers()) {
                    player.addFunds(Math.round(project.getTotalValue() * 0.5));
                    sendFundsUpdateToPlayer(player);
                }

                // Remove the project from employees map, so that employees are unassigned
                project.setEarnedValue(project.getTotalValue());
                iterator.remove();

                for (ProjectType type : ProjectType.values()) {
                    for (Employee employee : employees) {
                        logger.debug("{} XP: {} days", type, employee.getExperienceInDaysByProjectType(type));
                    }
                }
            }

            // Build event for new project state
            JSONObject projectObject = new JSONObject();
            projectObject.put("id", project.getId());
            projectObject.put(EARNED_VALUE, project.getEarnedValue());

            JSONObject event = new JSONObject();
            event.put(EVENT_TYPE, EventType.PROJECT_UPDATED);
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
            earnedValue /= numberOfParallelProjects;
            switch (numberOfParallelProjects) {
                case 1:
                    //noinspection ConstantConditions
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

            // Project ramp-up time influences earned value
            // Example:
            // 0 days XP = 0% productivity
            // 1 day XP = 10% productivity
            // 10 days XP = 50% productivity
            // 20 days XP = 100% productivity
            double x = employee.getExperienceInDaysByProject(project);
            if (x < 30) {
                double productivityFactor = 1.022595 - 1.02502 * exp(-0.1399307 * x);
                earnedValue = (int) (earnedValue * productivityFactor);
            }

            // Increase the employee's experience
            employee.gainExperience(project, 1);

            // Increase the project's earnedValue
            project.addEarnedValue(earnedValue, this.getCurrentTick());
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

    public int getCurrentTick() {
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

    void removeEmployeeFromAllProjects(Employee employee) {
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

    void removeEmployeeFromProject(Employee employee, Project project) {
        // Get current list of employees working on that project
        ArrayList<Employee> employees = projectEmployeesMap.get(project) ;

        if (employees.contains(employee)) {
            employees.remove(employee);
            projectEmployeesMap.put(project, employees);
            logger.debug("{} unassigned from {}", employee.getName(), project.getName());
        }
    }
}
