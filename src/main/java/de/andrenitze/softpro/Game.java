package de.andrenitze.softpro;

import com.google.gson.Gson;
import de.andrenitze.softpro.entities.GameOverStats;
import de.andrenitze.softpro.entities.Objective;
import de.andrenitze.softpro.entities.StoryElement;
import de.andrenitze.softpro.entities.StoryElements;
import de.andrenitze.softpro.events.GameEvent;
import de.andrenitze.softpro.types.EventType;
import de.andrenitze.softpro.types.ProjectType;
import org.hibernate.Session;
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
    private static final int GAME_SPEED_IN_MILLISECONDS = 200;
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
    private ArrayList<StoryElement> storyElements;

    // Every GameServer can host multiple Games
    Game(ConcurrentHashMap<WebSocket, Player> players, GameServer gameServer) {
        // Every game consists of players and a world in a specific state
        this.players = players;
        this.gameServer = gameServer;
        this.eventHandler = new GameEventHandler(this);
        currentTick = 0;
        currentDate = new Date();
        projects = new ArrayList<>();
        projectEmployeesMap = new ConcurrentHashMap<>();
        logger.info("A new game has started with {} players.", players.size());

        loadStoryElementsFromFile();

        // Start the round for all players
        GameEvent<Object> startEvent = new GameEvent<>();
        startEvent.setType(EventType.ROUND_STARTED);
        sendMessageToAllPlayers(GSON.toJson(startEvent));

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
            this.sendMessageToAllPlayers("{ \""+EVENT_TYPE+"\": \""+EventType.T+"\", \"payload\": " + getCurrentTick() + "}");

            // Progress game time and calculate the world's state for each tick
            progressGameTime();
        }, 0, GAME_SPEED_IN_MILLISECONDS, TimeUnit.MILLISECONDS);

    }

    private void loadStoryElementsFromFile() {
        StoryElements elements = new StoryElements();
        elements.loadStoryElementsFromYamlFile();
        this.storyElements = elements.getStoryElements();
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
        sendStoryElementsPerTick();

        long endTime = System.nanoTime();
        long timeElapsedInMilliseconds = (endTime - startTime) / 1000000;

        if (timeElapsedInMilliseconds >= 2) {
            logger.warn("Execution time of game loop: {} ms", timeElapsedInMilliseconds);
        }

    }

    private void sendStoryElementsPerTick() {
        // Check if there's a story element for today
        List<StoryElement> relevantStoryElements = new ArrayList<>();
        this.storyElements.forEach(element -> {
            // Relevant = Has not been sent AND (is scheduled earliest for this tick OR has an objective precondition)
            if (!element.isSent() && (element.getEarliestOccurrence() == currentTick ||
                    element.getAfterObjective() != 0)) {
                relevantStoryElements.add(element);
            }
        });

        if (relevantStoryElements.size() == 0) {
            return;
        }

        players.forEach((webSocket, player) -> {
            //ArrayList<Objective> activeObjectives = player.getActiveObjectivesUntilThisTick(currentTick);
            GameEvent<List<StoryElement>> newStoryElementEvent = new GameEvent<>(EventType.NEW_STORY_ELEMENT);
            List<StoryElement> thisPlayersStoryElements = new ArrayList<>();

            // Compile a list of all relevant story elements
            relevantStoryElements.forEach(storyElement -> {
                // Check if there are any required objectives before sending
                ArrayList<Objective> completedObjectives = player.getCompletedObjectives();
                if (storyElement.getAfterObjective() != 0) {
                    completedObjectives.forEach(objective -> {
                        if (storyElement.getAfterObjective() == objective.getId()) {
                            thisPlayersStoryElements.add(storyElement);
                            storyElement.setSent(true);
                        }
                    });
                } else {
                    thisPlayersStoryElements.add(storyElement);
                }
            });

            if (thisPlayersStoryElements.size() != 0) {
                // Send the compiled list to the player
                newStoryElementEvent.setPayload(thisPlayersStoryElements);
                sendMessageToPlayer(player, GSON.toJson(newStoryElementEvent));
            }
        });
    }

    private void updateEmployeeStatePerTick() {

    }

    private void checkObjectivesCriteriaAndSendRewardsPerTick() {
        players.forEach((webSocket, player) -> {
            GameEvent<List<Objective>> objectivesUpdatedEvent = new GameEvent<>(EventType.OBJECTIVES_UPDATED);
            List<Objective> allActiveObjectives;

            // Calculate progress for all active objectives
            for (Objective objective: player.getObjectives()) {
                // Naive matching approach with exact IDs
                if (objective.getId() == 1 && !objective.isCompleted()) {
                    // Were conditions met (= projects finished) after the objective occurred?
                    // Only check relevant (= finished) projects
                    ArrayList<Project> relevantProjects = (ArrayList<Project>) projects
                            .stream()
                            .filter(project -> project.isCompleted()
                                    && project.getCompletedTick() > objective.getEarliestOccurrence()
                                    && project.playerWasInvolved(player))
                            .collect(Collectors.toList());

                    // Only send when conditions have changed from last time
                    if (objective.getCompletedSteps() != relevantProjects.size()) {

                        // The number of relevant projects equals the completed steps
                        objective.setCompletedSteps(relevantProjects.size());

                        // Assemble and send update event
                        allActiveObjectives = player.getActiveObjectivesUntilThisTick(currentTick);
                        objectivesUpdatedEvent.setPayload(allActiveObjectives);
                        sendMessageToPlayer(player, GSON.toJson(objectivesUpdatedEvent));
                    }
                }
            }
        });
    }

    private void spawnObjectivesPerTick() {
        players.forEach((webSocket, player) -> {
            List<Objective> newObjectivesInThisTick = player.getNewObjectivesForThisTick(currentTick);
            if (!newObjectivesInThisTick.isEmpty()) {
                GameEvent<List<Objective>> objectivesUpdatedEvent = new GameEvent<>(EventType.OBJECTIVES_UPDATED);
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
        sendMessageToPlayer(player, GSON.toJson(newFundsEvent));
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

                GameOverStats goStats = new GameOverStats();
                goStats.setDeliveredProjects(deliveredProjects);
                goStats.setProjectsVolume(projectsVolume);
                goStats.setPlayerName(player.getName());
                goStats.setFinishedAt(new Date());
                goStats.setGameId(String.valueOf(this.hashCode()));
                goStats.setIpAddress(webSocket.getRemoteSocketAddress().toString());
                goStats.setSurvivedDays(this.getCurrentTick());

                try (Session session = this.gameServer.sessionFactory.openSession()) {
                    session.beginTransaction();
                    session.save(goStats);
                    session.getTransaction().commit();
                    session.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                stats.put("deliveredProjects", deliveredProjects);
                stats.put("projectsVolume", projectsVolume);
                gameOverEvent.setPayload(stats);

                sendMessageToPlayer(player, GSON.toJson(gameOverEvent));

                // Tell game server to move player back to lobby
                gameServer.addPlayer(webSocket, player);

                // Check if there's a new high-score and broadcast updates in lobby
                if (isNewHighScore(goStats)) {
                    gameServer.setNewHighScore(goStats);
                }

                kickPlayer(webSocket);
            }
        });
    }

    private boolean isNewHighScore(GameOverStats highScoreCandidate) {
        GameOverStats highScore;

        // Check database to see if this is a new high-score
        highScore = gameServer.getCurrentHighScore();
        if (highScore == null) return false;

        return (highScoreCandidate.getProjectsVolume() >= highScore.getProjectsVolume());
    }

    private void randomlySpawnProjectTendersPerTick() {
        if (new Random().nextFloat() >= 0.95) {
            // Generate a new project
            Project project = Project.generateRandomProject();
            projects.add(project);

            // Initialize project-employee map
            projectEmployeesMap.put(project, new ArrayList<>());

            // Inform players about the new tender
            GameEvent<Project> newTenderEvent = new GameEvent<>(EventType.NEW_TENDER);
            newTenderEvent.setPayload(project);
            logger.debug(String.valueOf(newTenderEvent));
            logger.debug(GSON.toJson(newTenderEvent));
            sendMessageToAllPlayers(GSON.toJson(newTenderEvent));
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
                        if (employee.getExperienceInDaysByProjectType(project.getType()) > 0) {
                            logger.debug("{}'s {} XP: {} days", employee.getName(), type, employee.getExperienceInDaysByProjectType(type));
                        }
                        if (employee.getExperienceInDaysByProjectDomain(project.getDomain()) > 0) {
                            logger.debug("{}'s {} Domain XP: {} days", employee.getName(), project.getDomain(), employee.getExperienceInDaysByProjectDomain(project.getDomain()));
                        }
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
