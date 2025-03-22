package de.andrenitze.softpro;

import de.andrenitze.softpro.config.GameParameters;
import de.andrenitze.softpro.domains.*;
import de.andrenitze.softpro.domains.accounting.AccountCategory;
import de.andrenitze.softpro.domains.accounting.AccountingEntry;
import de.andrenitze.softpro.domains.accounting.AccountingService;
import de.andrenitze.softpro.domains.accounting.TransactionType;
import de.andrenitze.softpro.domains.decisions.DecisionDAO;
import de.andrenitze.softpro.domains.decisions.OptionVoteDistribution;
import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.employees.EmployeeIdGenerator;
import de.andrenitze.softpro.domains.employees.StatusEffectType;
import de.andrenitze.softpro.domains.objectives.Mission;
import de.andrenitze.softpro.domains.objectives.Objective;
import de.andrenitze.softpro.domains.objectives.ObjectiveChecker;
import de.andrenitze.softpro.domains.projects.*;
import de.andrenitze.softpro.domains.story.StoryElement;
import de.andrenitze.softpro.domains.story.StoryElementsLoader;
import de.andrenitze.softpro.events.EventType;
import de.andrenitze.softpro.events.GameEvent;
import de.andrenitze.softpro.events.GameEventHandler;
import de.andrenitze.softpro.types.*;
import de.andrenitze.softpro.config.DatabaseConfig;
import lombok.Getter;
import org.java_websocket.WebSocket;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.net.ConnectException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

import static de.andrenitze.softpro.GameServer.*;
import static de.andrenitze.softpro.events.GameEventHandler.TEAM_SPIRIT;
import static de.andrenitze.softpro.GameServer.RANDOM;
import static java.lang.Math.*;
import static java.time.LocalDate.now;

public class Game {
    public static final int GAME_SPEED_IN_MILLISECONDS = 600;
    private static final String EVENT_TYPE = "type";
    public static final float PROJECT_SPAWN_PROBABILITY = 0.1f;
    public static final float COMPLIANCE_PROJECT_SPAWN_PROBABILITY = 0.01f;
    public static final int STALE_TENDERS_KILL_DAYS = 548;

    // Base productivity value = How much value one person (FTE) can produce in one day
    public static final int BASE_PRODUCTIVITY_VALUE = 1000;
    public static final double PROFIT_MARGIN = 0.3;
    public static final int NUMBER_OF_LEVELS_IN_THE_GAME = 3;
    public static final double DAYS_TO_LEARN_NEW_THINGS = 180; // 6 months to learn something new
    public static final String RESTORE_LOST_DATA = "Restore lost data";
    public static final int BACKUP_BLUES_LEVEL = 2;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    @Getter
    private final AccountingService accountingService;
    @Getter
    private final MessagingService messagingService;
    private boolean isRunning; // Game instance is active
    private boolean isPaused = false; // Game instance is active, but paused (e.g., for briefing and tutorials)
    private final GameServer gameServer;
    @Getter
    private final ConcurrentHashMap<WebSocket, Player> players;
    @Getter
    private int currentTick = 0;
    private LocalDate currentDate;
    private ScheduledExecutorService gameLoop;
    private final GameEventHandler eventHandler;
    private ArrayList<StoryElement> storyElements; // Level-specific
    @Getter
    private final SkillsManager skillsManager;
    @Getter
    private final TalentMarket talentMarket;
    @Getter
    private int level = 1;
    @Getter
    private final ProjectService projectService;
    private final PropertyChangeSupport support = new PropertyChangeSupport(this);

    /**
     * Creates a new Game with the provided Players within the GameServer. The game starts immediately.
     * <p>
     * A ThreadPool with a single Thread is used to run the game logic in a loop.
     * Changes in the game's state can be sent to the players as GameEvents.
     *
     * @param gameServer The GameServer that this game is running in
     */
    public Game(GameServer gameServer) {
        logger.debug("Creating a new game instance.");

        // Every game consists of players and a world in a specific state
        this.players = new ConcurrentHashMap<>();
        this.gameServer = gameServer;

        // The event handler of each game will receive events from the frontend
        // and decide how to change the game's state based on these events.
        this.eventHandler = new GameEventHandler(this);

        // Fill talent market with candidates, use global IDs for employees (unique across all games)
        EmployeeIdGenerator employeeIdGenerator = new EmployeeIdGenerator();
        talentMarket = new TalentMarket(employeeIdGenerator);
        talentMarket.clearTalentMarket();

        // Initialize the SkillsManager to manage players' skills across levels
        skillsManager = new SkillsManager();

        // Initialize the accounting service to keep track of all financial transactions
        accountingService = new AccountingService(this);

        // Initialize the project manager to manage all projects
        projectService = new ProjectService(this);

        // Initialize messaging service
        messagingService = new MessagingService(this);

        // Don't initialize the talent market for level 1
        if (level != 1) {
            initializeTalentMarket();
        }

        currentDate = now();
    }

    public void start() {
        // CHeck if there is at least one player in this game instance
        if (players.isEmpty()) {
            logger.error("No players in this game. Cannot start.");
            return;
        }

        // Start the round for all players
        isRunning = true;
        GameEvent<Object> startEvent = new GameEvent<>();
        startEvent.setType(EventType.ROUND_STARTED);
        messagingService.broadcastToAllPlayers(getGson().toJson(startEvent));

        // Send initial state to all players
        players.forEach((_, player) -> {
            // Send state
            logger.debug("Sending initial state to players");
            GameEvent<Player> initialPlayerEvent = new GameEvent<>(EventType.STATE_UPDATED);
            initialPlayerEvent.setPayload(player);
            messagingService.sendMessageToPlayer(player, getGson().toJson(initialPlayerEvent));
        });

        // Start running the game time
        gameLoop = Executors.newSingleThreadScheduledExecutor();
        gameLoop.scheduleWithFixedDelay(() -> {
            if (isPaused) {
                return;
            }

            // Notify all clients of current time
            messagingService.broadcastToAllPlayers("{ \""+EVENT_TYPE+"\": \""+EventType.T+
                    "\", \"payload\": " + getCurrentTick() + "}");

            // Progress game time and calculate the world's state for each tick
            progressGameTime();
        }, 750, GAME_SPEED_IN_MILLISECONDS, TimeUnit.MILLISECONDS);

        logger.info("A new game has started with {} players in level {}", players.size(), getLevel());
    }

    /**
     * The next level is prepared, after players hit the "Start Level X" (PLAYER_READY) button.
     */
    public void prepareNextLevel() {
        // Get next level from players. Highest level wins, but all players in one instance should have the same level.
        int nextLevel = 0;
        for (Player player : players.values()) {
            if (player.getLevel() > nextLevel) {
                nextLevel = player.getLevel();
            }
        }
        setLevel(nextLevel);

        logger.debug("prepareNextLevel(): Players' highest level (= {}) will be the next level", nextLevel);

        if (getLevel() != 1) {
            projectService.setProjects(new ArrayList<>());
            for (int i = 0; i < 100; i++) {
                Project project = new Project().initialize();

                // Set randomly negative publish dates to have some history of tenders
                project.setPublishedAt(round(RANDOM.nextFloat() * STALE_TENDERS_KILL_DAYS * -1));

                projectService.addProject(project);
                projectService.getProjectEmployeesMap().put(project, new ArrayList<>());
            }

            // Send talent market to players at once
            GameEvent<ArrayList<Employee>> employeeEvent = new GameEvent<>(EventType.TALENTS_ADDED);
            employeeEvent.setPayload(talentMarket.getTalents());
            messagingService.broadcastToAllPlayers(getGson().toJson(employeeEvent));
        }

        // Send all tenders at once
        GameEvent<ArrayList<Project>> projectEvent = new GameEvent<>(EventType.TENDERS_ADDED);
        projectEvent.setPayload(projectService.getProjects());
        messagingService.broadcastToAllPlayers(getGson().toJson(projectEvent));

        // Logic for decisions and their consequences
        // Beware: Decisions from previous levels might have consequences in other levels
        if (getLevel() == 1) {
            triggerLevel1Consequences();
        } else if (getLevel() == 2) {
            triggerLevel2Consequences();
        } else if (getLevel() == 3) {
            triggerLevel3Consequences();
        } else if (getLevel() == 4) {
            triggerLevel4Consequences();
        }

        // Add permanent employee status effects, if unlocked (e.g., "team-spirit")
        players.forEach((_, player) -> {
            logger.debug("Checking for permanent status effects for player {}", player.getId());
            if (skillsManager.playerHasSkill(player, TEAM_SPIRIT)) {
                logger.debug("Player {} has the skill {}", player.getId(), TEAM_SPIRIT);
                player.getEmployees().forEach(employee -> {
                    logger.debug("Adding permanent status effect {} to employee {}", TEAM_SPIRIT, employee.getId());
                    employee.addComplexStatusEffect(TEAM_SPIRIT);
                });
            }
        });

        players.forEach((_, player) -> {
            if (player.getDecisionsByLevel(getLevel()).isEmpty()) {
                logger.warn("Player {} has no decisions for level {}", player.getId(), getLevel());
            }
        });
    }


    private void triggerLevel1Consequences() {
        players.forEach((_, player) -> {
            // Decision "Fail to plan, plan to fail" (level 1, decision 1)
            int option = player.getDecisionsByLevel(1).getFirst().getOptionId();

            if (option == 1) {
                // Option 1 "Efficiency" -> Increase productivity by 75% for the whole level
                player.getEmployees().forEach(employee -> employee.addStatusEffect(
                        StatusEffectType.PRODUCTIVITY, 1.75f, "Efficient work organization"));
            } else if (option == 2) {
                // Option 2 "Creativity" -> Add an employee with salary = 0 for the whole level
                Employee freeEmployee = new Employee(talentMarket.generateNewEmployeeId());
                freeEmployee.setSalary( 0, 0);
                freeEmployee.setSatisfaction(0.7f);
                player.addEmployee(freeEmployee, 0);
            } else if (option == 3) {
                // Option 3 "Spontaneity" -> Add status effect "Stress" for the whole level (productivity -20%, satisfaction -10%)
                player.getEmployees().forEach(employee -> {
                    employee.addStatusEffect(
                            StatusEffectType.PRODUCTIVITY, 0.8f, "Spontaneous work organization");
                    employee.addStatusEffect(
                            StatusEffectType.SATISFACTION, 0.9f, "Spontaneous work organization");
                });
            }
        });
    }

    private void triggerLevel2Consequences() {
        // Adjust gameplay for each player according to decisions made in briefing
        players.forEach((_, player) -> {
            // "Backup decision" (level 2, decision 1)
            int option = player.getDecisionsByLevel(BACKUP_BLUES_LEVEL).getFirst().getOptionId();
            if (option == 1) {
                // Option 1 "Employee does it": Lower productivity of first employee as status effect for the whole level
                // Make sure that the effect stays even if employee is fired. Always use the first employee.
                player.setFunds(player.getFunds() - 5000);
                Employee firstEmployee = player.getEmployees().getFirst();
                firstEmployee.addStatusEffect(
                        StatusEffectType.PRODUCTIVITY, 0.8f, "Implementing backup solution");
                // All status effects will be reset when the next level is prepared

                // Option 2 "Do nothing": No effect in this level. Later on, the player will have to deal with the consequences
            } else if (option == 3) {
                // Option 3 "Vendor does it", decrease funds by 15000.
                player.setFunds(player.getFunds() - 15000);
            }
        });
    }

    private void triggerLevel3Consequences() {
        players.forEach((_, player) -> {
            // "Backup decision" (level 2, decision 1)
            int backupOption = player.getDecisionsByLevel(BACKUP_BLUES_LEVEL).getFirst().getOptionId();
            if (backupOption == 2) {
                // Dramatically decrease productivity of all employees as status effect for the whole level
                player.getEmployees().forEach(employee -> employee.addStatusEffect(
                        StatusEffectType.PRODUCTIVITY, 0.6f, RESTORE_LOST_DATA)
                );
            } else if (backupOption == 1) {
                // Slightly decrease productivity of all employees as status effect for the whole level
                player.getEmployees().forEach(employee -> employee.addStatusEffect(
                        StatusEffectType.PRODUCTIVITY, 0.95f, RESTORE_LOST_DATA)
                );
            }
        });
    }

    private void triggerLevel4Consequences() {
        players.forEach((_, player) -> {
            // "Backup decision"
            int backupOption = player.getDecisionsByLevel(BACKUP_BLUES_LEVEL).getFirst().getOptionId();
            if (backupOption == 2) {
                // Dramatically decrease productivity of all employees as status effect for the whole level
                player.getEmployees().forEach(employee -> employee.addStatusEffect(
                        StatusEffectType.PRODUCTIVITY, 0.2f, RESTORE_LOST_DATA)
                );
            } else if (backupOption == 1) {
                // Slightly decrease productivity of all employees as status effect for the whole level
                player.getEmployees().forEach(employee -> employee.addStatusEffect(
                        StatusEffectType.PRODUCTIVITY, 0.95f, RESTORE_LOST_DATA)
                );
            }
        });
    }

    private void setLevel(int i) {
        this.level = i;

        // Load problems for the level
        projectService.loadProblems();
    }

    void loadStory(int level) {
        this.storyElements = new StoryElementsLoader().getStoryElementsForLevel(level);
    }

    private void progressGameTime() {
        if (isPaused) {
            return;
        }

        ++currentTick;

        // Stop if the game is over
        if (!isRunning) {
            // Sure 'bout this?
            gameLoop.shutdownNow();
            return;
        }

        // Progress calendar date
        currentDate = now();
        currentDate = currentDate.plusDays(currentTick);

        long startTime = System.nanoTime();
        // Execute the game logic each "tick".
        // This is important because player interactions alter the state between ticks.
        // Order is important, because some methods depend on the state of others (side effects may occur).
        projectService.conductWorkOnAllProjects(currentTick, currentDate, projectService.getProjectEmployeesMap());
        projectService.cancelOverdueProjects(currentTick);
        accountingService.processMonthlyPayments(currentDate, players, currentTick);
        projectService.randomlySpawnProjectTenders();
        projectService.randomlySpawnComplianceProjects();
        projectService.removeStaleTenders();
        projectService.evaluateTenderProcesses();
        sendNewObjectives();
        checkObjectivesCriteriaAndSendRewards();
        simulateEmployeeLives();
        sendStoryElements();
        projectService.startStaleProjects();
        projectService.createProblemsInProjects();
        sendNewAccountingEntries();
        checkGameOverConditions();

        long endTime = System.nanoTime();
        long timeElapsedInMilliseconds = (endTime - startTime) / 1000000;

        if (timeElapsedInMilliseconds >= 20) {
            logger.warn("Execution time of game loop: {} ms", timeElapsedInMilliseconds);
        }
    }

    private void sendNewAccountingEntries() {
        // Send new accounting entries (the ones with tick == currentTick) to the corresponding players
        players.forEach((webSocket, player) -> {
            List<AccountingEntry> newEntries = accountingService.getAllEntriesByPlayer(player.getId()).stream()
                    .filter(entry -> entry.getDay() == currentTick)
                    .toList();

            if (!newEntries.isEmpty()) {
                GameEvent<List<AccountingEntry>> newAccountingEntriesEvent = new GameEvent<>(EventType.ACCOUNTING_ENTRIES_ADDED);
                newAccountingEntriesEvent.setPayload(newEntries);
                messagingService.sendMessageToPlayer(player, getGson().toJson(newAccountingEntriesEvent));
            }
        });
    }

    private void sendStoryElements() {
        // Check if there's a story element for today
        List<StoryElement> relevantStoryElements = getRelevantStoryElements();

        if (relevantStoryElements.isEmpty()) {
            return;
        }

        players.forEach((webSocket, player) -> {
            List<StoryElement> thisPlayersStoryElements = getPlayerStoryElements(relevantStoryElements, player);

            if (!thisPlayersStoryElements.isEmpty()) {
                // Send the compiled list to the player
                GameEvent<List<StoryElement>> newStoryElementEvent = new GameEvent<>(EventType.NEW_STORY_ELEMENT);
                newStoryElementEvent.setPayload(thisPlayersStoryElements);
                messagingService.sendMessageToPlayer(player, getGson().toJson(newStoryElementEvent));
            }
        });
    }

    private List<StoryElement> getRelevantStoryElements() {
        List<StoryElement> relevantStoryElements = new ArrayList<>();
        this.storyElements.forEach(element -> {
            // Relevant = Has not been sent AND (is scheduled earliest for this tick OR has an objective precondition)
            if (!element.isSent() && (element.getEarliestOccurrence() == currentTick ||
                    element.getAfterObjective() != 0)) {
                relevantStoryElements.add(element);
            }
        });
        return relevantStoryElements;
    }

    private List<StoryElement> getPlayerStoryElements(List<StoryElement> relevantStoryElements, Player player) {
        List<StoryElement> thisPlayersStoryElements = new ArrayList<>();
        relevantStoryElements.forEach(storyElement -> {
            // Check if there are any required objectives before sending
            ArrayList<Objective> completedObjectives = (ArrayList<Objective>) player.getCompletedObjectives();
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
        return thisPlayersStoryElements;
    }

    private void simulateEmployeeLives() {
        players.forEach((webSocket, player) -> player.getEmployees().forEach(employee -> {
            employee.liveLife(currentTick);
            boolean needsUpdate = employee.isSick() || employee.hasFirstDayAfterSickLeave(currentTick) || employee.removeExpiredStatusEffects();

            // Annual events that affect employees
            if (currentTick % 365 == 0) {
                employee.initializeSickDays();
            }

            // Monthly events that affect employees
            if (currentTick % 30 == 0) {
                // Send at least one update per month for metrics (i.e., utilization, sick days, satisfaction)
                needsUpdate = true;
            }

            // This could be refactored so that the "needsUpdate" logic can be used here as well
            projectService.applyStatusEffectsForStressfulOnboarding(player, employee);

            // Send an employee update, if anything has changed
            if (needsUpdate) {
                messagingService.sendEmployeeUpdate(player, employee);
            }
        }));
    }

    private void checkObjectivesCriteriaAndSendRewards() {
        ObjectiveChecker objectiveChecker = new ObjectiveChecker(this);
        players.forEach((webSocket, player) -> objectiveChecker.checkObjectives(player));
    }

    @Nullable
    public Mission getMissionByObjective(Player player, Objective objective) {
        // Get mission by objective
        Mission mission = player.getMissions().stream()
                .filter(m -> m.getObjectives().contains(objective))
                .findFirst()
                .orElse(null);

        if (mission == null) {
            logger.warn("Objective {} has no mission.", objective.getId());
            return null;
        }
        return mission;
    }



    void checkGameOverConditions() {
        players.forEach((webSocket, player) -> {
            if (player.isBankrupt() || player.completedAllObjectives()) {
                stopGameTime();
                handleGameOver(player, webSocket); // Decide what to do next
            }
        });
    }

    private void handleGameOver(Player player, WebSocket webSocket) {
        logger.debug("Game over for player {}.", player.getId());
        boolean playerHasWon = player.completedAllObjectives() && !player.isBankrupt();

        GameOverStats goStats = createGameOverStats(player);
        goStats.setReport(playerHasWon ? "win" : "fail");
        logger.debug("Result: {}", playerHasWon ? "win" : "fail");

        if (playerHasWon) {
            // Keep the player in the game and prepare for the next level
            logger.debug("Player {} has completed all {} missions. Moving to next level ({}).",
                    player.getId(),
                    player.getMissions().size(),
                    level + 1);

            // Only increase level for existing levels
            if (level < NUMBER_OF_LEVELS_IN_THE_GAME) {
                player.setLevel(level + 1);
                logger.debug("Player {} has reached level {}.", player.getId(), level + 1);
            } else {
                logger.debug("Player {} has reached the final level.", player.getId());
            }
        } else {
            logger.debug("Player {} has lost the game. Level stays the same. Try again! :)", player.getId());
        }

        // Send GAME_OVER event after decision
        GameEvent<GameOverStats> gameOverEvent = new GameEvent<>(EventType.GAME_OVER);
        gameOverEvent.setPayload(goStats);
        messagingService.sendMessageToPlayer(player, getGson().toJson(gameOverEvent));
        logger.debug("Sent GAME_OVER event to player.");

        // Update player one last time in this level to make sure, client is up-to-date
        GameEvent<Player> playerUpdateEvent = new GameEvent<>();
        playerUpdateEvent.setType(EventType.PLAYER_UPDATED);
        playerUpdateEvent.setPayload(player);
        webSocket.send(getGson().toJson(playerUpdateEvent));

        saveGameOverStats(webSocket, player, goStats); // This could be handled outside the game loop (DB access takes time...)
        checkAndBroadcastHighScore(goStats);

        // Move player back to lobby in any case. The frontend will send the player to the
        // lobby or briefing screen depending on the report (win/fail).
        // This means, that each level will have a new game instance.
        gameServer.movePlayerToLobby(webSocket, player);
        removePlayerFromGame(webSocket);
    }

    private GameOverStats createGameOverStats(Player player) {
        int deliveredProjects = 0;
        int projectsVolume = 0;
        for (Project project : projectService.getProjects()) {
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
        goStats.setLevel(getLevel());

        DecisionDAO dao = new DecisionDAO(DatabaseConfig.getDataSource());
        Map<Integer, List<OptionVoteDistribution>> distributions = null;
        try {
            distributions = dao.getVoteDistributionByLevel(level);
        } catch (ConnectException e) {
            logger.warn("Could not fetch vote distributions from database: {}", e.getMessage());
        }
        goStats.setCommunityVotes(distributions);

        return goStats;
    }

    private void saveGameOverStats(WebSocket webSocket, Player player, GameOverStats goStats) {
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
    }

    private void checkAndBroadcastHighScore(GameOverStats goStats) {
        // Check if there's a new high-score and broadcast updates in lobby
        if (isNewHighScore(goStats)) {
            gameServer.setNewHighScore(goStats);
            gameServer.broadcastLobbyState();
        }
    }

    // Note: This method sends an OBJECTIVES_UPDATED event (i.e., it includes ALL objectives of this level),
// because the frontend needs to show the completed objectives of other missions as well as the new objectives.
    void sendNewObjectives() {
        players.forEach((webSocket, player) -> {
            boolean thereAreNewObjectives = !player.getNewObjectivesByTick(getCurrentTick()).isEmpty();
            if (thereAreNewObjectives) {
                logger.debug("Sending {} new objectives to player.", player.getNewObjectivesByTick(getCurrentTick()).size());

                GameEvent<List<Objective>> objectivesUpdatedEvent = new GameEvent<>(EventType.OBJECTIVES_UPDATED);

                // For the frontend, still include ALL objectives, even completed ones, in this event
                List<Objective> allObjectives = player.getObjectivesUntilThisTick(getCurrentTick());
                objectivesUpdatedEvent.setPayload(allObjectives);
                messagingService.sendMessageToPlayer(player, getGson().toJson(objectivesUpdatedEvent));
            }
        });
    }

    private boolean isNewHighScore(GameOverStats highScoreCandidate) {
        List<GameOverStats> highScores;

        // Check database to see if this is a new high-score
        highScores = gameServer.getCurrentHighScores();
        if (highScores == null) return false;

        return highScores.stream().anyMatch(highScore ->
                highScore.getProjectsVolume() < highScoreCandidate.getProjectsVolume());
    }

    static float calculateXP(Project project) {
        // Riskier and larger projects yield more XP
        float xp = project.getTotalValue() / 1000f;
        switch (project.getRiskLevel()) {
            case low -> xp *= 0.75F;
            case medium -> xp *= 1;
            case high -> xp *= 2;
            case extreme -> xp *= 4;
        }

        // Compliance projects yield no XP
        if (project.getType() == ProjectType.COMPLIANCE) {
            xp = 0;
        }
        return xp;
    }

    void removePlayerFromGame(WebSocket key) {
        players.remove(key);
        support.firePropertyChange("players", null, players);
        closeGameIfEmpty();
    }

    boolean hasWebSocket(WebSocket conn) {
        return players.containsKey(conn);
    }

    GameEventHandler getEventHandler() {
        return eventHandler;
    }

    public Player getPlayerByWebSocket(WebSocket websocket) {
        return players.get(websocket);
    }

    public void closeGameIfEmpty() {
        int numberOfPlayers = players.size();

        // Close game session if this was the last player
        if (numberOfPlayers == 0) {
            // Stop the game loop to make sure the thread can be interrupted
            logger.debug("Game {} has no players left. Stopping game loop and closing game.", this.hashCode());

            // Stop the game loop asynchronously (no guarantees)
            shutdownAndAwaitTermination(gameLoop);

            // Remove the game from the server
            gameServer.removeGame(this);
        }
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

    public void stopGameTime() {
        isRunning = false;
    }

    public void addPlayerToGame(WebSocket key, Player value) {
        players.put(key, value);
        support.firePropertyChange("players", null, players);
    }

    public void assessProjectRiskForPlayer(int projectId, Player player) {
        Project project = projectService.getProjectById(projectId);
        if (project == null) {
            logger.error("Project with ID {} could not be found.", projectId);
            return;
        }

        // Deduct funds from player
        int riskAssessmentCost = (int) GameParameters.PROJECT_RISK_ASSESSMENT_COST;
        accountingService.addEntry(new AccountingEntry(
                player,
                getCurrentTick(),
                riskAssessmentCost,
                AccountCategory.DEBIT_PROJECTS,
                TransactionType.DEBIT,
                "Project risk assessment")
        );
        player.subtractFunds(riskAssessmentCost);
        messagingService.sendFundsUpdateToPlayer(player);

        // Send project update to player
        GameEvent<Project> riskAssessedConfirmation = new GameEvent<>(EventType.RISK_ASSESSMENT_CONFIRMED);
        riskAssessedConfirmation.setPayload(project);
        messagingService.sendMessageToPlayer(player, getGson().toJson(riskAssessedConfirmation));
    }

    public void generateFirstEmployeesForPlayers() {
        // Remove any existing employees from the player
        players.forEach((webSocket, player) -> player.getEmployees().clear());

        // Generate first employees for all players (necessary for Level 2)
        players.forEach((webSocket, player) -> talentMarket.generateFirstEmployees().forEach(
                employee -> player.addEmployee(employee, 0)
        ));
    }

    // Move Employee from Player back to TalentMarket
    public void dismissEmployee(Player player, Employee employee) {
        player.removeEmployee(employee);
        employee.removeAllStatusEffects();
        talentMarket.addTalent(employee);

        // If there are projectService.getProjects()...
        if (getProjectService().getProjects() != null) {
            projectService.removeEmployeeFromAllProjects(employee);
        }

        // Send employee dismissal confirmation
        GameEvent<Employee> employeeDismissedEvent = new GameEvent<>(EventType.EMPLOYEE_DISMISSED);
        employeeDismissedEvent.setPayload(employee);
        messagingService.sendMessageToPlayer(player, getGson().toJson(employeeDismissedEvent));

        // Send new employee to all players' TalentMarkets in the game
        GameEvent<ArrayList<Employee>> employeeEvent = new GameEvent<>(EventType.TALENTS_ADDED);
        employeeEvent.setPayload(new ArrayList<>(List.of(employee)));
        messagingService.broadcastToAllPlayers(getGson().toJson(employeeEvent));
    }

    void initializeTalentMarket() {
        talentMarket.clearTalentMarket();

        for (int i = 0; i < 30; i++) {
            Employee employee = new Employee(talentMarket.generateNewEmployeeId());
            talentMarket.addTalent(employee);
        }
        logger.debug("Talent market initialized with {} employees.", talentMarket.getTalents().size());
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void pause() {
        isPaused = true;
    }

    public void resume() {
        isPaused = false;
    }

    public void conductOneToOneMeeting(Player player, Employee employee) {
        // Increase satisfaction of employee
        employee.haveOneToOneMeeting();
        messagingService.sendEmployeeUpdate(player, employee);
    }

    public void addPropertyChangeListener(PropertyChangeListener pcl) {
        support.addPropertyChangeListener(pcl);
    }
}
