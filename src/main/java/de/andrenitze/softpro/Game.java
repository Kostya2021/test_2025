package de.andrenitze.softpro;

import de.andrenitze.softpro.config.DatabaseConfig;
import de.andrenitze.softpro.domains.GameOverStats;
import de.andrenitze.softpro.domains.decisions.DecisionDAO;
import de.andrenitze.softpro.domains.decisions.OptionVoteDistribution;
import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.employees.EmployeeIdGenerator;
import de.andrenitze.softpro.domains.objectives.Mission;
import de.andrenitze.softpro.domains.objectives.Objective;
import de.andrenitze.softpro.domains.objectives.ObjectiveChecker;
import de.andrenitze.softpro.domains.projects.Project;
import de.andrenitze.softpro.domains.projects.ProjectType;
import de.andrenitze.softpro.domains.story.StoryElement;
import de.andrenitze.softpro.domains.story.StoryElementsLoader;
import de.andrenitze.softpro.events.*;
import de.andrenitze.softpro.services.impl.*;
import de.andrenitze.softpro.services.impl.player.GamePlayerServiceImpl;
import lombok.Getter;
import org.java_websocket.WebSocket;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static de.andrenitze.softpro.GameServer.RANDOM;
import static de.andrenitze.softpro.events.GameEventHandler.TEAM_SPIRIT;
import static java.lang.Math.round;
import static java.time.LocalDate.now;

@Component
@Scope("prototype")
public class Game {
    public static final int MAX_NUMBER_OF_PLAYERS_PER_GAME = 4;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    @Getter private GamePlayerServiceImpl playerService;
    @Getter private SkillServiceImpl skillService;
    @Getter private AccountingServiceImpl accountingService;
    @Getter private MessagingServiceImpl messagingService;
    @Getter private TalentMarket talentMarket;
    @Getter private ProjectServiceImpl projectService;
    @Getter private EmployeeServiceImpl employeeService;
    @Getter private GameEventHandler eventHandler;
    @Getter private GameEventPublisher eventPublisher;
    @Getter private ProjectEmployeeMappingImpl projectEmployeeMapping;

    public static final int GAME_SPEED_IN_MILLISECONDS = 600;
    public static final int STALE_TENDERS_KILL_DAYS = 548;
    public static final int NUMBER_OF_LEVELS_IN_THE_GAME = 3;
    public static final String RESTORE_LOST_DATA = "Restore lost data";
    public static final int BACKUP_BLUES_LEVEL = 2;
    private boolean isRunning; // Game instance is active
    private boolean isPaused = false; // Game instance is active, but paused (e.g., for briefing and tutorials)
    @Getter private int tick = 0;
    @Getter private int level = 1;
    private ScheduledExecutorService gameLoop;
    private ArrayList<StoryElement> storyElements; // Level-specific
    private ObjectiveServiceImpl objectiveService;
    private LevelConsequencesService levelConsequencesService;

    /**
     * Creates a new Game instance.
     * <p>
     * A ThreadPool with a single Thread is used to run the game logic in a loop.
     * Changes in the game's state can be sent to the players as GameEvents.
     */
    @Autowired
    public Game(SkillServiceImpl skillService,
                AccountingServiceImpl accountingService,
                MessagingServiceImpl messagingService,
                GamePlayerServiceImpl playerService,
                TalentMarket talentMarket,
                ProjectServiceImpl projectService,
                EmployeeServiceImpl employeeService,
                GameEventHandler eventHandler,
                GameEventPublisher eventPublisher,
                ObjectiveServiceImpl objectiveService,
                LevelConsequencesService levelConsequencesService) {
        EmployeeIdGenerator employeeIdGenerator = new EmployeeIdGenerator();
        this.talentMarket = new TalentMarket(employeeIdGenerator);
        this.talentMarket.clear();

        this.skillService = skillService;
        this.accountingService = accountingService;
        this.messagingService = messagingService;
        this.playerService = playerService;
        this.projectService = projectService;
        this.employeeService = employeeService;
        this.eventHandler = eventHandler;
        this.eventPublisher = eventPublisher;
        this.objectiveService = objectiveService;
        this.levelConsequencesService = levelConsequencesService;

        // Don't initialize the talent market for level 1
        if (level != 1) {
            talentMarket.initialize();
        }
    }

    // Mandatory services are injected here
    public Game(GamePlayerServiceImpl playerService, SkillServiceImpl skillService) {
        this.playerService = playerService;
        this.skillService = skillService;
    }

    public void start() {
        // CHeck if there is at least one player in this game instance
        if (playerService.getPlayers().isEmpty()) {
            logger.error("No players in this game. Cannot start.");
            return;
        }

        // Start the round for all players
        isRunning = true;
        messagingService.broadcastEvent(EventType.ROUND_STARTED);
        messagingService.broadcastInitialState();

        // Start running the game time
        gameLoop = Executors.newSingleThreadScheduledExecutor();
        gameLoop.scheduleWithFixedDelay(() -> {
            if (isPaused) {
                return;
            }

            // Notify all clients of current time
            GameEvent<Integer> timerEvent = new GameEvent<>(EventType.T);
            timerEvent.setPayload(getTick());
            messagingService.broadcastEvent(timerEvent);

            logger.debug("Progressing game time...");

            // Progress game time and calculate the world's state for each tick
            progressGameTime();
        }, 750, GAME_SPEED_IN_MILLISECONDS, TimeUnit.MILLISECONDS);

        logger.info("A new game has started with {} players in level {}.", playerService.getPlayers().size(), getLevel());
    }

    /**
     * The next level is prepared, after players hit the "Start Level X" (PLAYER_READY) button.
     */
    public void prepareNextLevel() {
        // Get next level from getPlayersService().getPlayers(). Highest level wins, but all players in one instance should have the same level.
        int nextLevel = 0;
        for (Player player : playerService.getPlayers().values()) {
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

                getProjectEmployeeMapping().addProject(project, new ArrayList<>());
            }

            // Send talent market to players at once
            GameEvent<List<Employee>> employeeEvent = new GameEvent<>(EventType.TALENTS_ADDED);
            employeeEvent.setPayload(talentMarket.getTalents());
            messagingService.broadcastToAllPlayers(GameServer.getGson().toJson(employeeEvent));
        }

        // Send all tenders at once
        GameEvent<List<Project>> projectEvent = new GameEvent<>(EventType.TENDERS_ADDED);
        projectEvent.setPayload(projectService.getProjects());
        messagingService.broadcastToAllPlayers(GameServer.getGson().toJson(projectEvent));

        // Logic for decisions and their consequences
        // Beware: Decisions from previous levels might have consequences in other levels
        if (getLevel() == 1) {
            levelConsequencesService.triggerLevel1Consequences();
        } else if (getLevel() == 2) {
            levelConsequencesService.triggerLevel2Consequences();
        } else if (getLevel() == 3) {
            levelConsequencesService.triggerLevel3Consequences();
        } else if (getLevel() == MAX_NUMBER_OF_PLAYERS_PER_GAME) {
            levelConsequencesService.triggerLevel4Consequences();
        }

        // Add permanent employee status effects, if unlocked (e.g., "team-spirit")
        playerService.getPlayers().forEach((_, player) -> {
            logger.debug("Checking for permanent status effects for player {}", player.getId());
            if (skillService.playerHasSkill(player, TEAM_SPIRIT)) {
                logger.debug("Player {} has the skill {}", player.getId(), TEAM_SPIRIT);
                player.getEmployees().forEach(employee -> {
                    logger.debug("Adding permanent status effect {} to employee {}", TEAM_SPIRIT, employee.getId());
                    employee.addComplexStatusEffect(TEAM_SPIRIT);
                });
            }
        });

        playerService.getPlayers().forEach((_, player) -> {
            if (player.getDecisionsByLevel(getLevel()).isEmpty()) {
                logger.warn("Player {} has no decisions for level {}", player.getId(), getLevel());
            }
        });
    }

    private void setLevel(int i) {
        this.level = i;

        // Load problems for the level
        projectService.loadProblems(i);
    }

    public void loadStory(int level) {
        this.storyElements = new StoryElementsLoader().getStoryElementsForLevel(level);
    }

    private void progressGameTime() {
        LocalDate currentDate;
        if (isPaused) {
            return;
        }

        ++tick;

        // Progress calendar date
        currentDate = now();
        currentDate = currentDate.plusDays(tick);

        long startTime = System.nanoTime();
        // Execute the game logic each "tick".
        // This is important because player interactions alter the state between ticks.
        // Order is important, because some methods depend on the state of others (side effects may occur).
        projectService.conductWorkOnAllProjects(getTick(), getLevel(), currentDate, projectEmployeeMapping.getProjectEmployeesMap());
        projectService.cancelOverdueProjects(getTick(), getLevel());
        projectService.randomlySpawnProjectTenders(getTick(), getLevel());
        projectService.evaluateTenderProcesses(getTick());
        // Things not to do in the first level to ease the player into the game
        // The "level" param in the other methods are used for a similar decision and might be removed in the future.
        if (getLevel() != 1) {
            projectService.randomlyAssignComplianceProjects(getTick());
            projectService.removeStaleTenders(getTick());
            projectService.startStaleProjects(getTick());
        }
        projectService.createProblemsInProjects(getTick(), getLevel());
        accountingService.processMonthlyPayments(currentDate, playerService.getPlayers());
        accountingService.sendNewAccountingEntries();
        employeeService.simulateEmployeeLives();
        processNewObjectives(getTick());
        checkObjectivesCriteriaAndSendRewards();
        sendStoryElements();
        checkGameOverConditions();

        long endTime = System.nanoTime();
        long timeElapsedInMilliseconds = (endTime - startTime) / 1000000;

        // If time elapsed is more than 20 ms and game is not currently shutting down
        if (timeElapsedInMilliseconds >= 20 && isRunning) {
            logger.warn("Execution time of game loop: {} ms", timeElapsedInMilliseconds);
        }
    }

    public void processNewObjectives(int currentTick) {
        Map<Player, List<Objective>> newObjectivesMap = objectiveService.getNewObjectives(currentTick);
        newObjectivesMap.forEach((player, _) -> {
            GameEvent<List<Objective>> objectivesUpdatedEvent = new GameEvent<>(EventType.OBJECTIVES_UPDATED);
            List<Objective> allObjectives = player.getObjectivesUntilThisTick(currentTick);
            objectivesUpdatedEvent.setPayload(allObjectives);
            messagingService.sendEventToPlayer(player, objectivesUpdatedEvent);
        });
    }

    private void sendStoryElements() {
        // Check if there's a story element for today
        List<StoryElement> relevantStoryElements = getRelevantStoryElements();

        if (relevantStoryElements.isEmpty()) {
            return;
        }

        playerService.getPlayers().forEach((_, player) -> {
            List<StoryElement> thisPlayersStoryElements = getPlayerStoryElements(relevantStoryElements, player);

            if (!thisPlayersStoryElements.isEmpty()) {
                // Send the compiled list to the player
                GameEvent<List<StoryElement>> newStoryElementEvent = new GameEvent<>(EventType.NEW_STORY_ELEMENT);
                newStoryElementEvent.setPayload(thisPlayersStoryElements);
                messagingService.sendMessageToPlayer(player, GameServer.getGson().toJson(newStoryElementEvent));
            }
        });
    }

    private List<StoryElement> getRelevantStoryElements() {
        List<StoryElement> relevantStoryElements = new ArrayList<>();
        this.storyElements.forEach(element -> {
            // Relevant = Has not been sent AND (is scheduled earliest for this tick OR has an objective precondition)
            if (!element.isSent() && (element.getEarliestOccurrence() == tick ||
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

    private void checkObjectivesCriteriaAndSendRewards() {
        ObjectiveChecker objectiveChecker = new ObjectiveChecker(this);
        playerService.getPlayers().forEach((_, player) -> objectiveChecker.checkObjectives(player));
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
        playerService.getPlayers().forEach((webSocket, player) -> {
            if (player.isBankrupt() || player.completedAllObjectives()) {
                handleGameOver(player, webSocket); // Decide what to do next
            }
        });
    }

    private void handleGameOver(Player player, WebSocket webSocket) {
        logger.debug("Game over for player {}.", player.getId());
        boolean playerHasWon = player.completedAllObjectives() && !player.isBankrupt();

        GameOverStats goStats = createGameOverStats(this, player);
        goStats.setReport(playerHasWon ? "win" : "fail");

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
        messagingService.sendEventToPlayer(player, gameOverEvent);

        // Update player one last time in this level to make sure, client is up-to-date
        GameEvent<Player> playerUpdateEvent = new GameEvent<>(EventType.PLAYER_UPDATED);
        playerUpdateEvent.setPayload(player);
        messagingService.sendEventToPlayer(player, playerUpdateEvent);

        // Fire game over event for GameServer to handle (save high-score etc.)
        GameOverData gameOverData = new GameOverData(webSocket, player, goStats, this);
        GameOverEvent internalGameOverEvent = new GameOverEvent(this, gameOverData);
        eventPublisher.publishGameOverEvent(internalGameOverEvent);

        // Let the Game class handle player removal
        removePlayer(webSocket);
    }

    private GameOverStats createGameOverStats(Game game, Player player) {
        int deliveredProjects = 0;
        int projectsVolume = 0;
        for (Project project : game.getProjectService().getProjects()) {
            if (project.isCompleted() && project.playerWasInvolved(player)) {
                deliveredProjects++;
                projectsVolume += project.getTotalValue();
            }
        }

        GameOverStats goStats = new GameOverStats();
        goStats.setDeliveredProjects(deliveredProjects);
        goStats.setProjectsVolume(projectsVolume);
        goStats.setSurvivedDays(tick);
        goStats.setPlayedSeconds(tick * GAME_SPEED_IN_MILLISECONDS / 1000);
        goStats.setLevel(level);

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

    public record GameOverData(WebSocket webSocket, Player player, GameOverStats stats, Game game) {}

    public static float calculateXP(Project project) {
        // Riskier and larger projects yield more XP
        float xp = project.getTotalValue() / 1000f;
        switch (project.getRiskLevel()) {
            case LOW -> xp *= 0.75F;
            case MEDIUM -> xp *= 1;
            case HIGH -> xp *= 2;
            case EXTREME -> xp *= MAX_NUMBER_OF_PLAYERS_PER_GAME;
        }

        // Compliance projects yield no XP
        if (project.getType() == ProjectType.COMPLIANCE) {
            xp = 0;
        }
        return xp;
    }

    public void removePlayer(WebSocket key) {
        playerService.removePlayer(key);

        PlayersChangedEvent playersChangedEvent = new PlayersChangedEvent(this, playerService.getPlayers());
        eventPublisher.publishPlayersChangedEvent(playersChangedEvent);
    }

    @EventListener
    public void closeGameIfNoPlayersLeft(PlayersChangedEvent event) {
        int numberOfPlayers = event.getPlayers().size();

        // Close game session if this was the last player
        if (numberOfPlayers == 0) {
            // Stop the game loop to make sure the thread can be interrupted
            logger.debug("Game {} has no players left. Closing game...", this.hashCode());

            // Fire event for GameServer to handle
            GlobalGameEmptyEvent globalGameEmptyEvent = new GlobalGameEmptyEvent(this, this);
            eventPublisher.publishGameEmptyEvent(globalGameEmptyEvent);

            // Stop the game loop asynchronously (no guarantees)
            shutdownAndAwaitTermination(gameLoop);
        } else {
            logger.debug("Game {} has {} player(s) left. Keeping game instance alive.", this.hashCode(), numberOfPlayers);
        }
    }

    void shutdownAndAwaitTermination(ExecutorService pool) {
        if (pool == null) {
            return;
        }

        pool.shutdown();
        try {
            if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                    logger.error("Pool did not terminate");
                }
            }
        } catch (InterruptedException ie) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void addPlayer(WebSocket key, Player value) {
        logger.debug("Adding player {} to game.", value.getId());
        try {
            if (playerService.hasWebSocket(key)) {
                logger.warn("Player already exists in game. Ignoring request to add player.");
                return;
            } else if (playerService.getPlayers().size() >= MAX_NUMBER_OF_PLAYERS_PER_GAME) {
                logger.warn("Game is full. Cannot add player.");
                return;
            } else if (playerService.isPlayerInAnyGame(value)) {
                logger.warn("Player is already in another game. Cannot add player.");
                return;
            }

            playerService.addPlayer(key, value);

            PlayersChangedEvent playersChangedEvent = new PlayersChangedEvent(this, playerService.getPlayers());
            eventPublisher.publishPlayersChangedEvent(playersChangedEvent);
        } catch (Exception e) {
            logger.error("Could not add player to game: {}", e.getMessage());
        }
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
}
