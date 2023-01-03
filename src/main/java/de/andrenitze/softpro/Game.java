package de.andrenitze.softpro;

import com.google.gson.Gson;
import de.andrenitze.softpro.entities.GameOverStats;
import de.andrenitze.softpro.entities.Objective;
import de.andrenitze.softpro.entities.StoryElement;
import de.andrenitze.softpro.entities.StoryElements;
import de.andrenitze.softpro.events.GameEvent;
import de.andrenitze.softpro.types.EventType;
import org.hibernate.Session;
import org.java_websocket.WebSocket;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static java.lang.Math.exp;
import static java.time.LocalDate.now;

public class Game {
    private static final int GAME_SPEED_IN_MILLISECONDS = 500;
    private static final int BANKRUPTCY_THRESHOLD = -25000;
    private static final String EVENT_TYPE = "type";
    public static final float PROJECT_SPAWN_PROBABILITY = 0.05f;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private GameServer gameServer;
    private final ConcurrentHashMap<WebSocket, Player> players;
    private final ArrayList<Project> projects;
    private int currentTick;
    private LocalDate currentDate;
    private final ScheduledExecutorService gameLoop;
    private final GameEventHandler eventHandler;
    private final ConcurrentHashMap<Project, ArrayList<Employee>> projectEmployeesMap = new ConcurrentHashMap<>();
    private static final Gson GSON = new Gson();
    private ArrayList<StoryElement> storyElements;
    private SkillsManager skillsMananger = new SkillsManager();

    // Every GameServer can host multiple Games
    Game(ConcurrentHashMap<WebSocket, Player> players, GameServer gameServer) {
        // Every game consists of players and a world in a specific state
        this.players = players;
        this.gameServer = gameServer;
        this.eventHandler = new GameEventHandler(this);
        currentTick = 0;
        currentDate = now();

        // Spawn some projects to get going
        projects = new ArrayList<>();
        for (int i = 0; i<4; i++) {
            Project project = new Project();
            projects.add(project);
            projectEmployeesMap.put(project, new ArrayList<>());
            GameEvent<Project> newTenderEvent = new GameEvent<>(EventType.NEW_TENDER);
            newTenderEvent.setPayload(project);
            broadcastToAllPlayers(GSON.toJson(newTenderEvent));
        }

        logger.info("A new game has started with {} players.", players.size());

        loadStoryElementsFromFile();

        // Start the round for all players
        GameEvent<Object> startEvent = new GameEvent<>();
        startEvent.setType(EventType.ROUND_STARTED);
        broadcastToAllPlayers(GSON.toJson(startEvent));

        // Send initial state to all players
        players.forEach((webSocket, player) -> {
            // Initialize skills
            skillsMananger.addPlayer(player);

            // Send state
            logger.debug("Sending initial state to players");
            GameEvent<Player> initialPlayerEvent = new GameEvent<>(EventType.UPDATE_STATE);
            initialPlayerEvent.setPayload(player);
            sendMessageToPlayer(player, GSON.toJson(initialPlayerEvent));
        });

        // Start running the game time
        gameLoop = Executors.newSingleThreadScheduledExecutor();
        gameLoop.scheduleAtFixedRate(() -> {
            // Notify all clients of current time
            this.broadcastToAllPlayers("{ \""+EVENT_TYPE+"\": \""+EventType.T+
                    "\", \"payload\": " + getCurrentTick() + "}");

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
        currentDate = now();
        currentDate = currentDate.plusDays(currentTick);

        long startTime = System.nanoTime();
        // Execute these things each "tick" (naming convention: methodNamePerTick)
        // This is important because player interactions alter the state between ticks
        conductWorkOnAllProjectsPerTick();
        processSalariesAndAdjustFundsPerTick(currentDate);
        checkGameOverConditionsAndKickPlayersPerTick();
        randomlySpawnProjectTendersPerTick();
        assignProjectsPerTick();
        spawnObjectivesPerTick();
        checkObjectivesCriteriaAndSendRewardsPerTick();
        simulateEmployeeLifePerTick();
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

    private void simulateEmployeeLifePerTick() {
        players.forEach((webSocket, player) -> player.getEmployees().forEach(employee -> {
            employee.beAtWork(currentTick);

            if (employee.isSick() || employee.hasFirstDayAfterSickLeave(currentTick)) {
                sendEmployeeUpdate(player, employee);
            }

            if (currentTick % 365 == 0) {
                employee.initializeSickDays();
            }
        }));
    }

    private void sendEmployeeUpdate(Player player, Employee employee) {
        GameEvent<Employee> employeeUpdateEvent = new GameEvent<>(EventType.EMPLOYEE_UPDATED);
        employeeUpdateEvent.setPayload(employee);
        sendMessageToPlayer(player, GSON.toJson(employeeUpdateEvent));
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
                                    && project.getCompletedAt() > objective.getEarliestOccurrence()
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

    private void processSalariesAndAdjustFundsPerTick(LocalDate d) {
        if (d.getDayOfMonth() == 1) {
            players.forEach((webSocket, player) -> {
                player.calculateAndSubtractSalaries();
                sendFundsUpdateToPlayer(player);
            });
        }
    }

    private void sendFundsUpdateToPlayer(Player player) {
        GameEvent<Float> newFundsEvent = new GameEvent<>(EventType.NEW_FUNDS);
        newFundsEvent.setPayload(player.getFunds());
        sendMessageToPlayer(player, GSON.toJson(newFundsEvent));
    }

    private void checkGameOverConditionsAndKickPlayersPerTick() {
        players.forEach((webSocket, player) -> {
            boolean gameIsOver = false;
            boolean playerHasWon = false;

            if (player.getFunds() <= BANKRUPTCY_THRESHOLD) {
                // Game Over condition #1: Bankruptcy
                gameIsOver = true;
            } else if (player.getObjectives().size() != 0 &&
                    player.getObjectives().size() == player.getCompletedObjectives().size()) {
                // Game Over condition #2: All objectives completed
                gameIsOver = true;
                playerHasWon = true;
            }

            if (gameIsOver) {
                GameEvent<GameOverStats> gameOverEvent = new GameEvent<>(EventType.GAME_OVER);

                int deliveredProjects = 0;
                int projectsVolume = 0;
                for (Project project: this.getProjects()) {
                    if (project.isCompleted() && project.playerWasInvolved(player)) {
                        deliveredProjects++;
                        projectsVolume += project.getTotalValue();
                    }
                }

                GameOverStats goStats = new GameOverStats();
                goStats.setDeliveredProjects(deliveredProjects);
                goStats.setProjectsVolume(projectsVolume);
                goStats.setSurvivedDays(this.getCurrentTick());

                if (playerHasWon) {
                    goStats.setReport("win");
                } else {
                    goStats.setReport("fail");
                }

                gameOverEvent.setPayload(goStats);
                sendMessageToPlayer(player, GSON.toJson(gameOverEvent));

                // Keep connection and name but reset other player attributes
                player.initializeBeforeRound();

                // Tell game server to move player back to lobby
                gameServer.addPlayerToLobby(webSocket, player);

                // Complete the infos for the database
                goStats.setPlayerName(player.getName());
                goStats.setFinishedAt(new Date());
                goStats.setGameId(String.valueOf(this.hashCode()));
                goStats.setIpAddress(webSocket.getRemoteSocketAddress().toString());

                try (Session session = this.gameServer.sessionFactory.openSession()) {
                    session.beginTransaction();
                    session.save(goStats);
                    session.getTransaction().commit();
                    session.close();
                    logger.info("Game stats of player {} saved successfully.", player.getName());
                } catch (Exception e) {
                    e.printStackTrace();
                    logger.warn("Game stats of player {} could not be saved! Database up?", player.getName());
                }

                // Check if there's a new high-score and broadcast updates in lobby
                if (isNewHighScore(goStats)) {
                    gameServer.setNewHighScore(goStats);
                    gameServer.broadcastLobbyState();
                }

                // Remove player from the current game
                removePlayerFromGame(webSocket);
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
        if (new Random().nextFloat() <= PROJECT_SPAWN_PROBABILITY) {
            // Generate a new project
            Project project = new Project();
            projects.add(project);

            // Initialize project-employee map
            projectEmployeesMap.put(project, new ArrayList<>());

            // Inform players about the new tender
            GameEvent<Project> newTenderEvent = new GameEvent<>(EventType.NEW_TENDER);
            newTenderEvent.setPayload(project);
            broadcastToAllPlayers(GSON.toJson(newTenderEvent));
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

                    // Remember acquisition date
                    project.setAcquiredAt(currentTick);

                    // Inform winner with a confirmation message
                    GameEvent<Project> wonTenderEvent = new GameEvent<>();
                    wonTenderEvent.setType(EventType.PROJECT_RECEIVED);
                    wonTenderEvent.setPayload(project);
                    sendMessageToPlayer(project.getInvolvedPlayers().get(0), GSON.toJson(wonTenderEvent));
                }
            } else if (project.getTenderDeadlineInDays() != 0 && project.getTenderDeadlineInDays() != -1) {
                // Regular case: Just decrease the time left for tender participation
                project.decreaseTimeLeftForTender();
            }
        }
    }

    public void immediatelyCloseTender(Project project) {
        if (project.hasNoTenderProcess() && project.getInvolvedPlayers().size() == 1) {
            GameEvent<Integer> closeTenderEvent = new GameEvent<>(EventType.TENDER_CLOSED);
            closeTenderEvent.setPayload(project.getId());
            broadcastToAllPlayers(GSON.toJson(closeTenderEvent));

            // Make it appear in the next evaluation of assignProjectsPerTick()
            project.setTenderDeadlineInDays(0);
        }
    }

    private void conductWorkOnAllProjectsPerTick() {
        // No work on weekends
        if (currentDate.getDayOfWeek() == DayOfWeek.SATURDAY || currentDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return;
        }

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

            JSONObject event = new JSONObject();
            event.put(EVENT_TYPE, EventType.PROJECT_UPDATED);
            var projectObject = new JSONObject();

            // Finish the project
            if (project.isCompleted()) {
                // Send reward
                for (Player player : project.getInvolvedPlayers()) {
                    int profit = (int) Math.round(project.getTotalValue() * 0.6);

                    float overduePenaltyMultiplier = 1;
                    int daysLeft = project.getAcquiredAt() + project.getDeadline() - currentTick;
                    if (daysLeft < 0) {
                        // Per 1% delayed delivery, return 2% less win margin
                        overduePenaltyMultiplier = 1 - ((float) Math.abs(daysLeft) / project.getDeadline() * 2);
                        float penalty = profit * (1 - overduePenaltyMultiplier);
                        projectObject.put("penalty", penalty);
                        project.setPenalty(penalty);
                        logger.debug("Project finished, but was overdue. Reducing profit by {} as penalty.", penalty);
                    }
                    profit = (int) (profit * overduePenaltyMultiplier);
                    projectObject.put("profit", profit);
                    project.setProfit(profit);
                    player.addFunds(profit);
                    sendFundsUpdateToPlayer(player);

                    // Calculate player's XP points gained in this project
                    // Riskier and larger projects yield more XP
                    float xp = project.getTotalValue() / 100f;
                    switch (project.getRiskLevel()) {
                        case low -> xp *= 0.75;
                        case medium -> xp *= 1;
                        case high -> xp *= 2;
                        case extreme -> xp *= 4;
                    }
                    player.addXp((int) xp);

                    GameEvent<Player> playerUpdateEvent = new GameEvent<>();
                    playerUpdateEvent.setType(EventType.PLAYER_UPDATED);
                    playerUpdateEvent.setPayload(player);
                    sendMessageToPlayer(player, GSON.toJson(playerUpdateEvent));

                    // After project completion, send gained XP of employees to player
                    for (Employee employee : employees) {
                        sendEmployeeUpdate(player, employee);
                    }
                }

                // Calculate project result quality (0-100)
                // Low employee skill = low project quality
                int projectQuality;
                HashMap<Employee, Integer> projectExperience = new HashMap<>(10);
                Integer totalDaysWorkedOnProject = 0;
                var skillMultipliers = new HashMap<Employee, Float>();

                for (Employee employee : employees) {
                    float projectSkillMultiplier;
                    var projectTypeXP = employee.getExperienceInDaysByProjectType(project.getType());

                    // Rule: Employee skill increases with experience
                    // LaGrange interpolation from:
                    // 0 days of experience = 0% skill
                    // 720 days (= 2 years) of experience = 100% skill
                    // 2700 days (= 6 years) of experience = 200% skill
                    projectSkillMultiplier = (7 * projectTypeXP / 4380f) - (projectTypeXP * projectTypeXP / 3197400f);

                    // Remember for average calculation
                    skillMultipliers.put(employee, projectSkillMultiplier);
                    projectExperience.put(employee, employee.getExperienceInDaysByProject(project));
                    totalDaysWorkedOnProject += employee.getExperienceInDaysByProject(project);
                }

                // Average of all employees' skills weighted by days worked in the project
                var weightedProjectContributions = new HashMap<Employee, Float>();
                Integer finalTotalDaysWorkedOnProject = totalDaysWorkedOnProject;
                projectExperience.forEach((employee, XP) -> {
                    var contribution = (float) XP / (float) finalTotalDaysWorkedOnProject;
                    weightedProjectContributions.put(employee, contribution);
                    logger.debug("Project work done by {}: {}%", employee.getName(), weightedProjectContributions.get(employee)*100);
                });

                // Quality is the average of each employees' individual skill for this project weighted by the amount of work (= contribution)
                AtomicReference<Float> totalProjectQuality = new AtomicReference<>((float) 0);
                skillMultipliers.forEach((employee, skill) ->
                        totalProjectQuality.updateAndGet(quality -> quality + skill * weightedProjectContributions.get(employee)));

                projectQuality = (int) (totalProjectQuality.get() * 100);
                logger.debug("Project overall quality: {}/100", projectQuality);

                project.setQuality(projectQuality);
                projectObject.put("quality", projectQuality);

                // Remove the project from employees map, so that employees are unassigned
                project.setEarnedValue(project.getTotalValue());
                iterator.remove();
            }

            // Build a small custom event to just send new project progress and success metrics
            projectObject.put("id", project.getId());
            projectObject.put("earnedValue", project.getEarnedValue());

            if (project.isCompleted()) {
                projectObject.put("completedAt", currentTick);
            }

            event.put("payload", projectObject);

            // Send update to all involved players
            for (Player player : project.getInvolvedPlayers()) {
                sendMessageToPlayer(player, event.toString());
            }
        }
    }

    private void addEarnedValueForEachEmployee(Project project, ArrayList<Employee> employees) {
        int earnedValue;

        // Rule #3: Adding people to a late software project makes it later (Brooks' law)
        // New employees will decrease the whole team's productivity for on-boarding and training
        float onboardingFactor;
        if (!project.isRampingUp(currentTick)
                && project.hasOnboardingEmployees(employees)) {
            onboardingFactor = calculateOnboardingFactor(project, employees);
            logger.debug("Averaged onboarding factor (decreased productivity) for the whole team: {}", onboardingFactor);
        } else {
            // No onboarding required (safe period or no new employees
            onboardingFactor = 1;
        }

        for (Employee employee : employees) {
            if (employee.isSick()) {
                continue; // Go to next employee
            }

            // Fixed imaginary number
            earnedValue = 500;

            // Rule #1: Context changes decrease employee productivity
            int numberOfParallelProjects = getNumberOfParallelProjectsForEmployee(employee);
            earnedValue /= numberOfParallelProjects;
            switch (numberOfParallelProjects) {
                case 1 ->
                    //noinspection ConstantConditions
                        earnedValue *= 1;
                case 2 -> earnedValue *= 0.4;
                case 3 -> earnedValue *= 0.2;
                case 4 -> earnedValue *= 0.1;
                case 5 -> earnedValue *= 0.05;
                default -> earnedValue = 1;
            }

            // Rule #2: Productivity ramp-up: New staff needs some time to get fully productive
            // Example:
            // 0 days XP = 0% productivity
            // 1 day XP = 10% productivity
            // 10 days XP = 50% productivity
            // 20 days XP = 100% productivity
            float x = employee.getExperienceInDaysByProject(project);
            if (x < 30) {
                float productivityFactor = (float) (1.022595 - 1.02502 * exp(-0.1399307 * x));
                earnedValue *= productivityFactor;
            }

            // Increase the employee's experience
            employee.gainExperience(project, 1);

            earnedValue *= onboardingFactor;

            if (project.getEarnedValue() == 0 && earnedValue > 0) {
                project.setStartedAt(currentTick);
            }

            // Increase the project's earnedValue for this employee
            project.addEarnedValue(earnedValue, this.getCurrentTick());
        }
    }

    private float calculateOnboardingFactor(Project project, ArrayList<Employee> employees) {
        ArrayList<Float> onboardingFactors = new ArrayList<>();

        for (Employee employee : employees) {
            // FIXED: 30,4 (10% of a 304 day project)
            float onboardingDays = SimulationParameters.EMPLOYEE_ONBOARDING_TIME_IN_PERCENT * project.getScheduledDuration();

            // VARIABLE (depending on employees' experience): 4 days / 30,4 days = 0,1333%
            float onboardingProgress = employee.getExperienceInDaysByProject(project) / onboardingDays;
            if (onboardingProgress >= 1) {
                continue; // Onboarding completed. Ignore this employee for calculation.
            }

            // productivity factor = (1 - 0,1333) * 0,15 * 100 = 13% decrease
            // Example 0: 0% onboardingProgress => 15% productivity decrease
            // Example 1: 10% onboardingProgress => 13,5% productivity decrease
            // Example 2: 50% onboardingProgress => 7,5% productivity decrease
            // Example 3: 100% onboardingProgress => 0% productivity decrease
            float onboardingFactor = 1 - (1 - onboardingProgress) * SimulationParameters.MAXIMUM_ONBOARDING_PRODUCTIVITY_DECREASE;
            onboardingFactors.add(onboardingFactor);

            logger.debug("{} is being on-boarded in project {}: {} productivity factor, {}/{} days",
                    employee.getName(), project.getName(), onboardingFactor,
                    employee.getExperienceInDaysByProject(project), onboardingDays);
        }

        // Return average of all onboarding factors
        return (float) onboardingFactors.stream()
                .mapToDouble(d -> d)
                .reduce(1, (a, b) -> a * b);
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

    protected void broadcastToAllPlayers(String message) {
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

    public int getCurrentTick() {
        return currentTick;
    }

    void removePlayerFromGame(WebSocket conn) {
        players.remove(conn);
        closeIfEmpty();
    }

    Map<WebSocket, Player> getPlayers() {
        return players;
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

    boolean assignEmployeeToProject(Employee employee, Project project) {
        // Get current list of employees working on that project
        try {
            projectEmployeesMap.putIfAbsent(project, new ArrayList<>());
            ArrayList<Employee> employees = projectEmployeesMap.get(project);

            if (!employees.contains(employee)) {
                employees.add(employee);
                projectEmployeesMap.put(project, employees);
                logger.debug("{} assigned to {}", employee.getName(), project.getName());
                return true;
            } else {
                return false;
            }
        } catch (NullPointerException e) {
            logger.error(e.toString());
        }
        return false;
    }

    boolean removeEmployeeFromProject(Employee employee, Project project) {
        // Get current list of employees working on that project
        ArrayList<Employee> employees = projectEmployeesMap.get(project) ;

        if (employees.contains(employee)) {
            employees.remove(employee);
            projectEmployeesMap.put(project, employees);
            logger.debug("{} unassigned from {}", employee.getName(), project.getName());
            return true;
        }
        return false;
    }

    public boolean closeIfEmpty() {
        // Close game session if this was the last player
        if (players.size() == 0) {
            logger.info("Shutting down empty game.");

            gameServer = null;
            gameLoop.shutdownNow();
            return true;
        }
        return false;
    }

    public SkillsManager getSkillsMananger() {
        return skillsMananger;
    }
}
