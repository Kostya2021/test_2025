package de.andrenitze.softpro;

import com.google.gson.Gson;
import de.andrenitze.softpro.entities.GameOverStats;
import de.andrenitze.softpro.entities.Objective;
import de.andrenitze.softpro.entities.StoryElement;
import de.andrenitze.softpro.entities.StoryElements;
import de.andrenitze.softpro.events.GameEvent;
import de.andrenitze.softpro.types.DecisionDAO;
import de.andrenitze.softpro.types.EventType;
import de.andrenitze.softpro.types.GameOverStatsDAO;
import de.andrenitze.softpro.types.OptionVoteDistribution;
import de.andrenitze.softpro.util.DatabaseConfig;
import org.java_websocket.WebSocket;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static java.lang.Math.exp;
import static java.lang.Math.round;
import static java.time.LocalDate.now;

public class Game {
    private static final int GAME_SPEED_IN_MILLISECONDS = 1000;
    // Hashmap for each levels' bankruptcy threshold
    private static final HashMap<Integer, Integer> BANKRUPTCY_THRESHOLD = new HashMap<>() {{
        put(1, -500);
        put(2, -100000);
        put(3, 0);
        put(4, 0);
        put(5, 0);
        put(6, 0);
        put(7, 0);
    }};
    private static final String EVENT_TYPE = "type";
    public static final float PROJECT_SPAWN_PROBABILITY = 0.1f;
    public static final int STALE_TENDERS_KILL_DAYS = 548;

    // Base productivity value = How much value one person (FTE) can produce in one day
    public static final int BASE_PRODUCTIVITY_VALUE = 1000;
    public static final double PROFIT_MARGIN = 0.3;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private boolean isRunning;
    private final GameServer gameServer;
    private final ConcurrentHashMap<WebSocket, Player> players;
    private ArrayList<Project> projects = new ArrayList<>();
    private int currentTick;
    private LocalDate currentDate;
    private ScheduledExecutorService gameLoop;
    private final GameEventHandler eventHandler;
    private final ConcurrentHashMap<Project, ArrayList<Employee>> projectEmployeesMap = new ConcurrentHashMap<>();
    public static final Gson GSON = new Gson();
    private ArrayList<StoryElement> storyElements;
    private final SkillsManager skillsMananger = new SkillsManager();
    private final TalentMarket talentMarket;
    private int level = 1;

    /**
     * Creates a new Game with the provided Players within the GameServer. The game starts immediately.
     * <p>
     * A ThreadPool with a single Thread is used to run the game logic in a loop.
     * Changes in the game's state can be sent to the players as GameEvents.
     *
     * @param gameServer The GameServer that this game is running in
     */
    public Game(GameServer gameServer) {
        // Every game consists of players and a world in a specific state
        this.players = new ConcurrentHashMap<>();
        this.gameServer = gameServer;
        this.eventHandler = new GameEventHandler(this);

        // Fill talent market with candidates, use global IDs for employees (unique across all games)
        EmployeeIdGenerator employeeIdGenerator = new EmployeeIdGenerator();
        talentMarket = new TalentMarket(employeeIdGenerator);
        initializeTalentMarket();

        currentTick = 0;
        currentDate = now();
        loadStoryElementsFromFile();
    }

    public void start() {
        // CHeck if there is at least one player in this game instance
        if (players.isEmpty()) {
            logger.error("No players in this game instance. Cannot start game.");
            return;
        }

        // Start the round for all players
        isRunning = true;
        GameEvent<Object> startEvent = new GameEvent<>();
        startEvent.setType(EventType.ROUND_STARTED);
        broadcastToAllPlayers(GSON.toJson(startEvent));

        // Send initial state to all players
        players.forEach((webSocket, player) -> {
            // Initialize skills
            skillsMananger.addPlayer(player);

            // Send state
            logger.debug("Sending initial state to players");
            GameEvent<Player> initialPlayerEvent = new GameEvent<>(EventType.STATE_UPDATED);
            initialPlayerEvent.setPayload(player);
            sendMessageToPlayer(player, GSON.toJson(initialPlayerEvent));
        });

        prepareNextLevel();

        // Start running the game time
        gameLoop = Executors.newSingleThreadScheduledExecutor();
        gameLoop.scheduleWithFixedDelay(() -> {
            // Notify all clients of current time
            this.broadcastToAllPlayers("{ \""+EVENT_TYPE+"\": \""+EventType.T+
                    "\", \"payload\": " + getCurrentTick() + "}");

            // Progress game time and calculate the world's state for each tick
            progressGameTime();
        }, 750, GAME_SPEED_IN_MILLISECONDS, TimeUnit.MILLISECONDS);

        logger.info("A new game has started with {} players in level {}: {}",
                players.size(),
                getLevel(),
                players.values().stream().map(Player::getName).collect(Collectors.toList()));
    }

    /**
     * The next level is prepared, after players hit the "Start Level X" button.
     */
    private void prepareNextLevel() {
        if (getLevel() != 1) {
            projects = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                Project project = new Project();

                // Set randomly negative publish dates to have some history of tenders
                project.setPublishedAt((int) round(Math.random() * STALE_TENDERS_KILL_DAYS * -1));

                projects.add(project);
                projectEmployeesMap.put(project, new ArrayList<>());
            }

            // Send talent market to players at once
            GameEvent<ArrayList<Employee>> employeeEvent = new GameEvent<>(EventType.TALENTS_ADDED);
            employeeEvent.setPayload(talentMarket.getTalents());
            broadcastToAllPlayers(GSON.toJson(employeeEvent));
        }

        // Send all tenders at once
        GameEvent<ArrayList<Project>> projectEvent = new GameEvent<>(EventType.TENDERS_ADDED);
        projectEvent.setPayload(projects);
        broadcastToAllPlayers(GSON.toJson(projectEvent));
    }

    protected int getLevel() {
        return level;
    }

    private void loadStoryElementsFromFile() {
        StoryElements elements = new StoryElements();
        elements.loadStoryElementsFromYamlFile();
        this.storyElements = elements.getStoryElements();
    }

    private void progressGameTime() {
        ++currentTick;

        // Stop if the game is over
        if (!isRunning) {
            gameLoop.shutdownNow();
            return;
        }

        //logger.debug("Tag: {} | Spieler: {}", currentTick, players.values().stream().map(Player::getName).collect(Collectors.toList()));

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
        removeStaleTendersPerTick();
        assignProjectsPerTick();
        spawnObjectivesPerTick();
        checkObjectivesCriteriaAndSendRewardsPerTick();
        simulateEmployeeLifePerTick();
        sendStoryElementsPerTick();
        startStaleProjectsPerTick();

        long endTime = System.nanoTime();
        long timeElapsedInMilliseconds = (endTime - startTime) / 1000000;

        if (timeElapsedInMilliseconds >= 20) {
            logger.warn("Execution time of game loop: {} ms", timeElapsedInMilliseconds);
        }
    }

    private void startStaleProjectsPerTick() {
        // Don't do that in level 1
        if (getLevel() == 1) {
            return;
        }

        // For all projects that have been acquired, but not started after MAX(30 days, 10% of project duration)
        for (Project project : projects) {
            if (project.getAcquiredAt() != 0 && project.getStartedAt() == 0) {
                int daysPassed = currentTick - project.getAcquiredAt();
                if (daysPassed >= Math.max(30, project.getScheduledDuration() / 10)) {
                    // Start the project and inform involved players
                    startProject(project, currentTick-1);
                    notifyInvolvedPlayers(project);
                    logger.debug("Project {} force started after {} days.", project.getName(), daysPassed);
                }
            }
        }
    }

    private void notifyInvolvedPlayers(Project project) {
        GameEvent<HashMap<String, Integer>> projectStartedEvent = new GameEvent<>(EventType.PROJECT_STARTED);
        HashMap<String, Integer> payload = new HashMap<>();
        payload.put("projectId", project.getId());
        payload.put("startedAt", project.getStartedAt());
        projectStartedEvent.setPayload(payload);

        // Notify involved players about forced start
        project.getInvolvedPlayers().forEach(player -> sendMessageToPlayer(player, GSON.toJson(projectStartedEvent)));
    }

    private void removeStaleTendersPerTick() {
        // Remove tenders that have been on the market for a long time and store them in a separate array
        List<Project> staleTenders = new ArrayList<>();
        for (Iterator<Project> iterator = projects.iterator(); iterator.hasNext();) {
            Project project = iterator.next();
            if (project.getEarnedValue() == 0 &&
                    project.getInvolvedPlayers().isEmpty() &&
                    project.getPublishedAt() + STALE_TENDERS_KILL_DAYS < this.currentTick) {
                // Add the tender to the list of stale tenders
                staleTenders.add(project);

                // Remove the current element from the iterator and the list
                iterator.remove();
            }
        }

        // Send an update to the clients, if there are any stale tenders
        if (staleTenders.isEmpty()) {
            return;
        }
        GameEvent<List<Project>> tendersRemovedEvent = new GameEvent<>(EventType.TENDERS_REMOVED);
        tendersRemovedEvent.setPayload(staleTenders);
        broadcastToAllPlayers(GSON.toJson(tendersRemovedEvent));
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

        if (relevantStoryElements.isEmpty()) {
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

            if (!thisPlayersStoryElements.isEmpty()) {
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
            boolean updatedNeeded;

            // Calculate progress for all active (= incomplete) objectives
            for (Objective objective: player.getObjectives()) {
                updatedNeeded = false;

                if (objective.isCompleted()) {
                    continue;
                }

                /*
                  Naive matching approach with exact IDs from level-1-objectives.yaml
                  Create a new objective in the YAML file, then create a matching case here.
                 */
                if (objective.getId() == 11) {
                    // Criterion: If player has accepted any project from the project market
                    if (projects.stream().anyMatch(project -> project.getInvolvedPlayers().contains(player))) {
                        objective.markAsCompleted();
                        updatedNeeded = true;
                    }
                } else if (objective.getId() == 12) {
                    // Criterion: If employees have been assigned to any project
                    if (projectEmployeesMap.values().stream().anyMatch(employees -> employees.contains(player.getEmployees().get(0)))) {
                        objective.markAsCompleted();
                        updatedNeeded = true;
                    }
                } else if (objective.getId() == 13) {
                    // Criterion: If project has been kicked off
                    if (projectEmployeesMap.values().stream().anyMatch(employees -> employees.contains(player.getEmployees().get(0)))
                            && projectEmployeesMap.keySet().stream().anyMatch(project -> project.getStartedAt() != 0)) {
                        objective.markAsCompleted();
                        updatedNeeded = true;
                    }
                } else if (objective.getId() == 14 || objective.getId() == 21) {
                    // Were conditions met (= projects finished) after the objective occurred?
                    // Only check relevant (= finished) projects
                    ArrayList<Project> relevantProjects = (ArrayList<Project>) projects
                            .stream()
                            .filter(project -> project.isCompleted()
                                    && project.getCompletedAt() > objective.getEarliestOccurrence()
                                    && project.playerWasInvolved(player))
                            // The following line can NOT be replaced with "toList()"!
                            .collect(Collectors.toList());

                    // Only send when conditions have changed from last time
                    if (objective.getCompletedSteps() != relevantProjects.size()) {
                        // The number of relevant projects equals the completed steps
                        objective.setCompletedSteps(relevantProjects.size());
                        updatedNeeded = true;
                    }
                }

                if (updatedNeeded) {
                    allActiveObjectives = player.getActiveObjectivesUntilThisTick(currentTick);
                    objectivesUpdatedEvent.setPayload(allActiveObjectives);
                    sendMessageToPlayer(player, GSON.toJson(objectivesUpdatedEvent));
                }
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

    void checkGameOverConditionsAndKickPlayersPerTick() {
        players.forEach((webSocket, player) -> {
            boolean gameIsOver = false;
            boolean playerHasWon = false;

            if (player.getFunds() <= BANKRUPTCY_THRESHOLD.get(level)) {
                // Game Over condition #1: Bankruptcy
                gameIsOver = true;
            } else if (!player.getObjectives().isEmpty() &&
                    player.getObjectives().size() == player.getCompletedObjectives().size()) {
                logger.debug("Objectives completed: {} / {}", player.getCompletedObjectives().size(), player.getObjectives().size());
                logger.debug("Objective 1 '{}' is completed: {}", player.getObjectives().get(0).getTitle(), player.getObjectives().get(0).isCompleted());

                // Game Over condition #2: All objectives completed
                gameIsOver = true;
                playerHasWon = true;

                // Reset objectives for the next level
                player.getObjectives().clear();

                // Set the player's level to the next one. This will be used to initialize the
                // correct game state for the next level.
                logger.debug("Player {} has completed all objectives. Moving to next level.", player.getId());
                player.setLevel(level + 1);
            }

            if (gameIsOver) {
                GameEvent<GameOverStats> gameOverEvent = new GameEvent<>(EventType.GAME_OVER);

                int deliveredProjects = 0;
                int projectsVolume = 0;
                for (Project project : getProjects()) {
                    if (project.isCompleted() && project.playerWasInvolved(player)) {
                        deliveredProjects++;
                        projectsVolume += project.getTotalValue();
                    }
                }

                GameOverStats goStats = new GameOverStats();
                goStats.setDeliveredProjects(deliveredProjects);
                goStats.setProjectsVolume(projectsVolume);
                goStats.setSurvivedDays(getCurrentTick());
                goStats.setPlayedSeconds(getCurrentTick() * GAME_SPEED_IN_MILLISECONDS / 1000);

                if (playerHasWon) {
                    goStats.setReport("win");
                } else {
                    goStats.setReport("fail");
                }

                // Add community votes to the game over stats
                DecisionDAO dao = new DecisionDAO(DatabaseConfig.getDataSource());
                Map<Integer, List<OptionVoteDistribution>> distributions = dao.getVoteDistributionByLevel(level);
                goStats.setCommunityVotes(distributions);

                gameOverEvent.setPayload(goStats);
                sendMessageToPlayer(player, Game.GSON.toJson(gameOverEvent));

                // Keep connection and name but reset other player attributes
                player.initializeBeforeGame(level);

                // Tell game server to move player back to lobby/ briefing screen
                this.gameServer.addPlayerToLobby(webSocket, player);

                DataSource dataSource = DatabaseConfig.getDataSource();
                GameOverStatsDAO gameOverStatsDAO = new GameOverStatsDAO(dataSource);

                // Complete the infos for the database
                goStats.setPlayerName(player.getName());
                goStats.setFinishedAt(new Date());
                goStats.setGameId(String.valueOf(this.hashCode()));
                goStats.setIpAddress(webSocket.getRemoteSocketAddress().toString());

                // Save high-score in a separate thread
                if (gameOverStatsDAO.saveGameOverStats(goStats)) {
                    logger.info("Game stats of player in game {} saved successfully.", goStats.getGameId());
                } else {
                    logger.warn("Game stats of player in game {} could not be saved!", goStats.getGameId());
                }

                // Check if there's a new high-score and broadcast updates in lobby
                if (isNewHighScore(goStats)) {
                    gameServer.setNewHighScore(goStats);
                    gameServer.broadcastLobbyState();
                }

                // Remove player from the current game
                removePlayerFromGame(webSocket);

                // If the game is empty, stop progressing the game time
                closeIfEmpty();
            }
        });
    }

    void spawnObjectivesPerTick() {
        players.forEach((webSocket, player) -> {
            List<Objective> newObjectivesInThisTick = player.getNewObjectivesForThisTick(getCurrentTick());
            if (!newObjectivesInThisTick.isEmpty()) {
                GameEvent<List<Objective>> objectivesUpdatedEvent = new GameEvent<>(EventType.OBJECTIVES_UPDATED);
                List<Objective> allActiveObjectives = player.getActiveObjectivesUntilThisTick(getCurrentTick());
                objectivesUpdatedEvent.setPayload(allActiveObjectives);
                sendMessageToPlayer(player, Game.GSON.toJson(objectivesUpdatedEvent));
            }
        });
    }

    private void sendFundsUpdateToPlayer(Player player) {
        GameEvent<Float> newFundsEvent = new GameEvent<>(EventType.NEW_FUNDS);
        newFundsEvent.setPayload(player.getFunds());
        sendMessageToPlayer(player, GSON.toJson(newFundsEvent));
    }

    private boolean isNewHighScore(GameOverStats highScoreCandidate) {
        GameOverStats highScore;

        // Check database to see if this is a new high-score
        highScore = gameServer.getCurrentHighScore();
        if (highScore == null) return false;

        return (highScoreCandidate.getProjectsVolume() >= highScore.getProjectsVolume());
    }

    private void randomlySpawnProjectTendersPerTick() {
        // Don't spawn new projects in level 1
        if (getLevel() == 1) {
            return;
        }

        if (new Random().nextFloat() <= PROJECT_SPAWN_PROBABILITY) {
            // Generate a new project
            Project project = new Project();
            project.setPublishedAt(getCurrentTick());
            projects.add(project);

            // Initialize project-employee map
            projectEmployeesMap.put(project, new ArrayList<>(2));

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

        // For all projects that are started AND have employees assigned
        Iterator<Map.Entry<Project, ArrayList<Employee>>> iterator = projectEmployeesMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Project, ArrayList<Employee>> entry = iterator.next();
            Project project = entry.getKey();
            ArrayList<Employee> employees = entry.getValue();

            // Ignore not started projects
            if (project.getStartedAt() == 0) {
                continue;
            }

            // Ignore empty projects
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
                    int profit = (int) round(project.getTotalValue() * PROFIT_MARGIN);

                    float overduePenaltyMultiplier = 1;
                    int daysLeft = project.getDeadline() - (currentTick - project.getStartedAt());
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

                    // Calculate player's XP gained in this project
                    // Riskier and larger projects yield more XP
                    float xp = project.getTotalValue() / 1000f;
                    switch (project.getRiskLevel()) {
                        case low -> xp *= 0.75F;
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
            // No onboarding required (safe period or no new employees)
            onboardingFactor = 1;
        }

        for (Employee employee : employees) {
            if (employee.isSick()) {
                continue; // Go to next employee
            }

            // Fixed imaginary number
            earnedValue = BASE_PRODUCTIVITY_VALUE;

            // Rule #4: Productivity depends on experience.
            // Experience factors are "project domain" and "project type".
            // The more experience an employee has in a project type, the more productive they are.
            // If an employee has *type* experience, but not the exact *domain* experience, they have some transferable
            // skills, which still makes them somewhat productive.
            // Examples:
            // 0 days type XP, 0 days domain XP = 0% productivity
            // 1000 days type XP, 0 days domain XP = 50% productivity
            // 1000 days type XP, 1000 days domain XP = 100% productivity

            // Experience in days for project type and domain
            int typeXP = employee.getExperienceInDaysByProjectType(project.getType());
            int domainXP = employee.getExperienceInDaysByProjectDomain(project.getDomain());

            float productivityFactor = getProductivityFactor(typeXP, domainXP);

            earnedValue *= productivityFactor * 2;

            // Rule #1: Context changes decrease employee productivity.
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

            // Rule #2: Productivity ramp-up: New staff in project needs some time to get fully productive.
            // Example:
            // 0 days XP = 0% productivity
            // 1 day XP = 10% productivity
            // 10 days XP = 50% productivity
            // 20 days XP = 100% productivity
            float x = employee.getExperienceInDaysByProject(project);
            if (x < 30) {
                float rampUpProductivityFactor = (float) (1.022595 - 1.02502 * exp(-0.1399307 * x));
                earnedValue *= rampUpProductivityFactor;
            }

            // Increase the employee's experience
            employee.gainExperience(project, 1);

            earnedValue *= onboardingFactor;

            if (project.getEarnedValue() == 0 && earnedValue > 0) {
                project.setStartedAt(currentTick);
            }

            // Rule #5: Organizational skills affect productivity.
            earnedValue *= calculateSkillsFactor(project);

            // Increase the project's earnedValue for this employee
            project.addEarnedValue(earnedValue, this.getCurrentTick());
        }
    }

    private static float getProductivityFactor(int typeXP, int domainXP) {
        // Weights for project type and domain experience
        float typeXPWeight = 0.25f;
        float domainXPWeight = 0.75f;

        // Base productivity value (if experience = 0)
        float baseProductivity = 0.25f;
        float maxProductivity = 1.0f;

        return (float) (baseProductivity +
                (maxProductivity - baseProductivity) * (
                        (typeXP > 0 ? typeXPWeight * (1 - exp(-0.0005 * typeXP)) : 0) +
                                (domainXP > 0 ? domainXPWeight * (1 - exp(-0.0005 * domainXP)) : 0)
                )
        );
    }

    private float calculateSkillsFactor(Project project) {
        List<Player> players = project.getInvolvedPlayers();

        // If only one player is working on the project
        if (players.size() == 1) {
            Player player = players.get(0);
            if (skillsMananger.playerHasSkill(player, "pmo")) {
                return 1.05f;
            }
        }

        return 1.0f;
    }

    private float calculateOnboardingFactor(Project project, ArrayList<Employee> employees) {
        ArrayList<Float> onboardingFactors = new ArrayList<>();

        for (Employee employee : employees) {
            // FIXED: 30,4 (10% of a 304 day project)
            float onboardingDays = Params.EMPLOYEE_ONBOARDING_TIME_IN_PERCENT * project.getScheduledDuration();

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
            float onboardingFactor = 1 - (1 - onboardingProgress) * Params.MAXIMUM_ONBOARDING_PRODUCTIVITY_DECREASE;
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
            Project project = entry.getKey();

            if (employees.contains(employee) && project.hasBeenStarted()) {
                numberOfProjects++;
            }
        }
        return numberOfProjects;
    }

    protected void broadcastToAllPlayers(String message) {
        // Send the message to all players
        players.forEach((webSocket, player) -> webSocket.send(message));
    }

    void sendMessageToPlayer(Player player, String message) {
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
        //closeIfEmpty();
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

    void assignEmployeeToProject(Employee employee, Project project) {
        // Get current list of employees working on that project
        try {
            projectEmployeesMap.putIfAbsent(project, new ArrayList<>());
            ArrayList<Employee> employees = projectEmployeesMap.get(project);

            // Add employee to project if not already assigned
            if (!employees.contains(employee)) {
                employees.add(employee);
                projectEmployeesMap.put(project, employees);
                logger.debug("{} assigned to {}", employee.getName(), project.getName());
            } else {
            }
        } catch (NullPointerException e) {
            logger.error(e.toString());
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

    public boolean closeIfEmpty() {
        int numberOfPlayers = players.size();

        // Close game session if this was the last player
        if (numberOfPlayers == 0) {
            // Stop the game loop to make sure the thread can be interrupted
            logger.debug("Game has no players left. Stopping game loop.");
            stop();

            // If the game loop successfully stopped, return true
            // TODO FIX THIS: It's probably not actually waiting for the game loop to shutdown
            return gameLoop.isShutdown();
        }
        return false;
    }

    void shutdownAndAwaitTermination(ExecutorService pool) {
        // Make sure, pool isn't null
        if (pool == null) {
            return;
        }

        pool.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                pool.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!pool.awaitTermination(2, TimeUnit.SECONDS))
                    logger.error("Pool did not terminate");
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            pool.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }

    public SkillsManager getSkillsManager() {
        return skillsMananger;
    }

    public void stop() {
        isRunning = false;
        shutdownAndAwaitTermination(gameLoop);
    }

    public void addPlayerToGame(WebSocket key, Player value) {
        players.put(key, value);
    }

    public TalentMarket getTalentMarket() {
        return talentMarket;
    }

    public void assessProjectRiskForPlayer(int projectId, Player player) {
        Project project = getProjectById(projectId);
        if (project == null) {
            logger.error("Project with ID {} not found.", projectId);
            return;
        }

        // Deduct funds from player
        player.subtractFunds(Params.PROJECT_RISK_ASSESSMENT_COST);
        sendFundsUpdateToPlayer(player);

        // Send project update to player
        GameEvent<Project> riskAssessedConfirmation = new GameEvent<>(EventType.RISK_ASSESSMENT_CONFIRMED);
        riskAssessedConfirmation.setPayload(project);
        sendMessageToPlayer(player, GSON.toJson(riskAssessedConfirmation));
    }

    public void generateFirstEmployeesForPlayers() {
        // Remove any existing employees from the player
        players.forEach((webSocket, player) -> player.getEmployees().clear());

        // Generate first employees for all players (necessary for Level 2)
        players.forEach((webSocket, player) -> {
            talentMarket.generateFirstEmployees().forEach(player::addEmployee);
        });
    }

    // Move Employee from Player back to TalentMarket
    public void dismissEmployee(Player player, Employee employee) {
        player.removeEmployee(employee);
        talentMarket.addTalent(employee);

        // If there are projects...
        if (projects != null) {
            // Remove employee from all projects
            for (Project project : projects) {
                if (projectEmployeesMap.containsKey(project)) {
                    ArrayList<Employee> employees = projectEmployeesMap.get(project);
                    employees.remove(employee);
                    projectEmployeesMap.put(project, employees);
                }
            }
        }

        // Send employee dismissal confirmation
        GameEvent<Employee> employeeDismissedEvent = new GameEvent<>(EventType.EMPLOYEE_DISMISSED);
        employeeDismissedEvent.setPayload(employee);
        sendMessageToPlayer(player, GSON.toJson(employeeDismissedEvent));

        // Send new employee to all players' TalentMarkets in the game
        GameEvent<ArrayList<Employee>> employeeEvent = new GameEvent<>(EventType.TALENTS_ADDED);
        employeeEvent.setPayload(new ArrayList<>(List.of(employee)));
        broadcastToAllPlayers(GSON.toJson(employeeEvent));
    }

    private void initializeTalentMarket() {
        talentMarket.clearTalentMarket();

        for (int i = 0; i < 30; i++) {
            Employee employee = new Employee(talentMarket.generateNewEmployeeId());
            talentMarket.addTalent(employee);
        }
        logger.debug("Talent market initialized with {} employees.", talentMarket.getTalents().size());
    }

    public void startProject(Project project, int startedAt) {
        if (project == null) {
            logger.error("Project not found.");
            return;
        }

        project.setStartedAt(startedAt);
    }

    public void addProject(Project project) {
        // Check if project id already exists, if not, add the project
        if (getProjectById(project.getId()) == null) {
            projects.add(project);
        }
    }
}
