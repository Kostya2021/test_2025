package de.andrenitze.softpro;

import de.andrenitze.softpro.config.DatabaseConfig;
import de.andrenitze.softpro.domains.GameOverStats;
import de.andrenitze.softpro.domains.decisions.DecisionDAO;
import de.andrenitze.softpro.domains.decisions.OptionVoteDistribution;
import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.objectives.Objective;
import de.andrenitze.softpro.domains.projects.Project;
import de.andrenitze.softpro.domains.projects.ProjectSummary;
import de.andrenitze.softpro.domains.projects.ProjectType;
import de.andrenitze.softpro.domains.story.StoryElement;
import de.andrenitze.softpro.events.*;
import de.andrenitze.softpro.services.GameLifeCycleService;
import de.andrenitze.softpro.services.impl.*;
import de.andrenitze.softpro.services.impl.player.GamePlayerServiceImpl;
import lombok.Getter;
import lombok.Setter;
import org.java_websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static de.andrenitze.softpro.events.GameEventHandler.TEAM_SPIRIT;
import static de.andrenitze.softpro.services.impl.player.GamePlayerServiceImpl.MAX_NUMBER_OF_PLAYERS_PER_GAME;

@Component
@Scope("prototype")
public class Game {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    @Getter private StoryService storyService;
    @Getter private GamePlayerServiceImpl playerService;
    @Getter private SkillServiceImpl skillService;
    @Getter private AccountingServiceImpl accountingService;
    @Getter private MessagingServiceImpl messagingService;
    @Getter private TalentMarket talentMarket;
    @Getter private ProjectServiceImpl projectService;
    @Getter private EmployeeServiceImpl employeeService;
    @Getter @Setter private GameEventHandler eventHandler;
    @Getter private GameEventPublisher eventPublisher;
    @Getter private ProjectEmployeeMappingImpl projectEmployeeService;
    @Setter @Getter private GameLifeCycleService lifeCycle;
    @Setter private ObjectiveServiceImpl objectiveService;
    private LevelConsequencesService levelConsequencesService;
    private ScheduledExecutorService gameLoop;

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
                ProjectServiceImpl projectService,
                EmployeeServiceImpl employeeService,
                GameEventHandler eventHandler,
                GameEventPublisher eventPublisher,
                LevelConsequencesService levelConsequencesService,
                GameLifeCycleService lifeCycle,
                ProjectEmployeeMappingImpl projectEmployeeMapping,
                StoryService storyService) {
        this.talentMarket = new TalentMarket();
        this.talentMarket.clear();

        this.skillService = skillService;
        this.accountingService = accountingService;
        this.messagingService = messagingService;
        this.playerService = playerService;
        this.projectService = projectService;
        this.employeeService = employeeService;
        this.eventHandler = eventHandler;
        this.eventPublisher = eventPublisher;
        this.levelConsequencesService = levelConsequencesService;
        this.lifeCycle = lifeCycle;
        this.projectEmployeeService = projectEmployeeMapping;
        this.storyService = storyService;

        // Don't initialize the talent market for level 1
        if (lifeCycle.getLevel() != 1) {
            talentMarket.initialize();
        }
    }

    // Mandatory services are injected here
    public Game(StoryService storyService, GamePlayerServiceImpl playerService, SkillServiceImpl skillService, GameLifeCycleService lifeCycle) {
        this.storyService = storyService;
        this.playerService = playerService;
        this.skillService = skillService;
        this.lifeCycle = lifeCycle;
    }

    public void start() {
        logger.debug("start()'ing game loop with {} players.", playerService.getPlayers().size());

        // CHeck if there is at least one player in this game instance
        if (playerService.getPlayers().isEmpty()) {
            logger.error("No players in this game. Cannot start.");
            return;
        }

        // Start the round for all players
        lifeCycle.run();
        messagingService.broadcast(EventType.ROUND_STARTED);
        messagingService.broadcastInitialPlayerState();

        // Broadcast the initial state of the game to all players
        GameEvent<List<Project>> newTendersEvent = new GameEvent<>(EventType.TENDERS_ADDED);
        newTendersEvent.setPayload(projectService.getProjects());
        messagingService.broadcast(newTendersEvent);

        // Now: TalentMarket
        GameEvent<List<Employee>> newTalentsEvent = new GameEvent<>(EventType.TALENTS_ADDED);
        newTalentsEvent.setPayload(talentMarket.getTalents());
        messagingService.broadcast(newTalentsEvent);

        // Start running the game time
        gameLoop = Executors.newSingleThreadScheduledExecutor();
        gameLoop.scheduleWithFixedDelay(() -> {
            if (lifeCycle.isPaused()) {
                return;
            }

            // Notify all clients of current time
            GameEvent<Integer> timerEvent = new GameEvent<>(EventType.TICK);
            timerEvent.setPayload(lifeCycle.getTick());
            messagingService.broadcast(timerEvent);

            progressGameTime();
        }, 750, lifeCycle.getGameSpeedInMilliseconds(), TimeUnit.MILLISECONDS);

        logger.info("A new game has started with {} players in level {}.", playerService.getPlayers().size(), lifeCycle.getLevel());
    }

    /**
     * Execute the game logic each "tick". Player interactions can alter the state between ticks.
     * Order is important, because some methods depend on the state of others (side effects are likely).
     */
    private void progressGameTime() {
        if (lifeCycle.isPaused()) {
            return;
        }

        lifeCycle.nextTick();

        long startTime = System.nanoTime();

        List<Project> updatedProjects = projectService.conductWorkOnAllProjects(
                lifeCycle.getTick(), lifeCycle.getLevel(), lifeCycle.getCurrentDate()
        );

        for (Project project : updatedProjects) {
            projectService.getProjectSummaryIfProgressChanged(project, lifeCycle.getTick())
                    .ifPresent(summary -> {
                        GameEvent<ProjectSummary> event = new GameEvent<>(EventType.PROJECT_UPDATED);
                        event.setPayload(summary);
                        project.getInvolvedPlayers().forEach(player ->
                                messagingService.sendToPlayer(player, event)
                        );
                    });
        }


        projectService.cancelOverdueProjects(lifeCycle.getTick(), lifeCycle.getLevel());
        projectService.evaluateTenderProcesses(lifeCycle.getTick());
        // Things not to do in the first level
        // The "level" param in the other methods are used for a similar decision and might be removed in the future.
        if (lifeCycle.getLevel() != 1) {
            projectService.spawnTenders(lifeCycle.getTick());
            projectService.spawnComplianceProjects(lifeCycle.getTick());
            notifyPlayersAboutStaleTenders();
            projectService.startStaleProjects(lifeCycle.getTick());
        } else {
            // Generate player-specific (easy) tenders in level 1
            projectService.randomlySpawnLevel1Tenders(lifeCycle.getTick(), playerService.getRandomPlayer());
        }
        projectService.createProblemsInProjects(lifeCycle.getTick(), lifeCycle.getLevel());
        accountingService.processMonthlyPayments(lifeCycle.getCurrentDate(), playerService.getPlayers(), lifeCycle.getTick(), lifeCycle.getLevel());
        messagingService.sendNewAccountingEntries(accountingService.getAccountingEntriesByTick(lifeCycle.getTick()));
        employeeService.simulateEmployeeLives(lifeCycle.getTick());

        Map<Player, List<Objective>> newObjectivesMap = objectiveService.getNewObjectives(lifeCycle.getTick());
        for (Map.Entry<Player, List<Objective>> entry : newObjectivesMap.entrySet()) {
            Player player = entry.getKey();
            List<Objective> allObjectives = player.getObjectivesUntilThisTick(lifeCycle.getTick());
            GameEvent<List<Objective>> gameEvent = new GameEvent<>(EventType.OBJECTIVES_UPDATED);
            gameEvent.setPayload(allObjectives);
            messagingService.sendToPlayer(player, gameEvent);
        }
        checkObjectivesCriteriaAndSendRewards();

        // Send updates for all projects which have been completed or cancelled in this tick
        projectService.getProjects().stream()
                .filter(project -> project.isCompleted() && project.getCompletedAt() == lifeCycle.getTick() ||
                        project.getCancelledAt() == lifeCycle.getTick())
                .forEach(project -> project.getInvolvedPlayers().forEach(player ->
                        messagingService.sendProjectUpdate(player, project)));

        // Send project tenders published in this tick
        projectService.getProjects().stream()
                .filter(project -> project.getPublishedAt() == lifeCycle.getTick())
                .forEach(project -> {
                    // Broadcast new tenders
                    GameEvent<Project> newTenderEvent = new GameEvent<>(EventType.NEW_TENDER);
                    newTenderEvent.setPayload(project);
                    messagingService.broadcast(newTenderEvent);

                    // If the tender is a compliance project, send individual events to all players
                    if (project.getType() == ProjectType.COMPLIANCE) {
                        project.getInvolvedPlayers().forEach(player -> {
                            project.addParty(player); // Important for frontend

                            // Immediately assign project (hence PROJECT_RECEIVED, not NEW_TENDER)
                            GameEvent<Project> complianceProjectEvent = new GameEvent<>(EventType.PROJECT_RECEIVED);
                            complianceProjectEvent.setPayload(project);
                            messagingService.sendToPlayer(player, complianceProjectEvent);
                            logger.debug("New compliance project spawned for all players: {}", project.getName());
                        });
                    }
                });

        // Send force-started project notification
        projectService.getProjects().stream()
                .filter(project -> project.getStartedAt() == lifeCycle.getTick())
                .forEach(project -> {
                    GameEvent<HashMap<String, Integer>> projectStartedEvent = new GameEvent<>(EventType.PROJECT_STARTED);
                    HashMap<String, Integer> payload = new HashMap<>();
                    payload.put("projectId", project.getId());
                    payload.put("startedAt", project.getStartedAt());
                    projectStartedEvent.setPayload(payload);

                    // Notify involved players about forced start
                    project.getInvolvedPlayers().forEach(player ->
                            messagingService.sendToPlayer(player, projectStartedEvent));
                });


        // For all players in the game...
        playerService.getPlayers().forEach((ignored, player) -> {
            sendAnyProjectChanges();
            sendAnyPlayerChanges(player);
            sendAnyNewStoryElements(player);
        });

        checkGameOverConditions();

        long endTime = System.nanoTime();
        long timeElapsedInMilliseconds = (endTime - startTime) / 1000000;

        // If time elapsed is more than 20 ms and game is not currently shutting down
        if (timeElapsedInMilliseconds >= 20 && lifeCycle.isRunning()) {
            logger.warn("Execution time of game loop: {} ms", timeElapsedInMilliseconds);
        }
    }

    /**
     * The next level is prepared, after players hit the "Start Level X" (PLAYER_READY) button.
     */
    void prepareLevelForPlayer(int level) {
        setLevel(level);
        logger.debug("Preparing level {} for player.", lifeCycle.getLevel());

        if (lifeCycle.getLevel() != 1) {
            projectService.initializeProjectMarket(level);

            GameEvent<List<Employee>> employeeEvent = new GameEvent<>(EventType.TALENTS_ADDED);
            employeeEvent.setPayload(talentMarket.getTalents());
            messagingService.broadcast(employeeEvent);
        }

        // Trigger level consequences
        switch (lifeCycle.getLevel()) {
            case 1 -> levelConsequencesService.triggerLevel1Consequences();
            case 2 -> levelConsequencesService.triggerLevel2Consequences();
            case 3 -> levelConsequencesService.triggerLevel3Consequences();
            case MAX_NUMBER_OF_PLAYERS_PER_GAME -> levelConsequencesService.triggerLevel4Consequences();
            default -> logger.warn("No level consequences for level {}", lifeCycle.getLevel());
        }

        // Add permanent status effects to players
        playerService.getPlayers().forEach((ignored, somePlayerInTheGame) -> {
            logger.debug("Checking for permanent status effects for player {}", somePlayerInTheGame.getId());
            if (skillService.playerHasSkill(somePlayerInTheGame, TEAM_SPIRIT)) {
                logger.debug("Player {} has the skill {}", somePlayerInTheGame.getId(), TEAM_SPIRIT);
                somePlayerInTheGame.getEmployees().forEach(employee -> {
                    logger.debug("Adding permanent status effect {} to employee {}", TEAM_SPIRIT, employee.getId());
                    employee.addComplexStatusEffect(TEAM_SPIRIT);
                });
            }
        });

        playerService.getPlayers().forEach((ignored, somePlayerInTheGame) -> {
            if (somePlayerInTheGame.getDecisionsByLevel(lifeCycle.getLevel()).isEmpty()) {
                logger.warn("Player {} has no decisions for level {}", somePlayerInTheGame.getId(), lifeCycle.getLevel());
            }
        });
    }

    private void setLevel(int level) {
        lifeCycle.setLevel(level);
        projectService.loadProblems(level);
        projectService.initializeProjectMarket(level);
        storyService.loadStory(level);
    }

    private void sendAnyNewStoryElements(Player player) {
        List<StoryElement> newStoryElements = storyService.getNewStoryElementsForPlayer(player, lifeCycle.getTick());
        if (!newStoryElements.isEmpty()) {
            GameEvent<List<StoryElement>> storyEvent = new GameEvent<>(EventType.STORY_ELEMENTS_ADDED);
            storyEvent.setPayload(newStoryElements);
            messagingService.sendToPlayer(player, storyEvent);
        }
    }

    private void notifyPlayersAboutStaleTenders() {
        // Notify players about stale tenders
        List<Project> staleTenders = projectService.getStaleTenders(lifeCycle.getTick());
        GameEvent<List<Project>> staleTendersEvent = new GameEvent<>(EventType.TENDERS_REMOVED);
        staleTendersEvent.setPayload(staleTenders);
        messagingService.broadcast(staleTendersEvent);
    }

    /**
     * Go through all projects and compare their hashes to find out if something significant has changed.
     */
    private void sendAnyProjectChanges() {
        playerService.getPlayers().forEach((ignored, player) -> {
            List<Project> currentProjects = projectService.getProjectsByPlayer(player);

            for (Project project : currentProjects) {
                Project previousState = projectService.getPreviousState(project.getId());

                if (previousState == null || project.hasChanged(previousState)) {
                    logger.debug("Project '{}' has changed since last tick. Sending new state.", project.getName());

                    try {
                        GameEvent<Project> projectUpdateEvent = new GameEvent<>(EventType.PROJECT_UPDATED);
                        projectUpdateEvent.setPayload(project);
                        messagingService.sendToPlayer(player, projectUpdateEvent);

                        projectService.updatePreviousState(project);
                    } catch (Exception e) {
                        logger.error("Error while sending project update: {}", e.getMessage());
                    }
                }
            }
        });
    }

    private void sendAnyPlayerChanges(Player player) {
        try {
            Player previousState = playerService.getPreviousState(player);

            if (previousState == null || player.hasChanged(previousState)) {
                logger.debug("Player '{}' has changed since last tick. Sending new state.", player.getId());

                GameEvent<Player> playerUpdateEvent = new GameEvent<>(EventType.PLAYER_UPDATED);
                playerUpdateEvent.setPayload(player);
                messagingService.sendToPlayer(player, playerUpdateEvent);

                // Save a copy of the current state for future comparison
                playerService.updatePreviousState(player);
            }
        } catch (Exception e) {
            logger.error("Error while sending player update: {}", e.getMessage());
        }
    }

    private void checkObjectivesCriteriaAndSendRewards() {
        playerService.getPlayers().forEach((ignored, player) -> {
            if (objectiveService.areThereObjectivesUpdates(player)) {
                logger.debug("Sending updated objectives to player.");
                try {
                    List<Objective> allActiveObjectives = player.getObjectivesUntilThisTick(lifeCycle.getTick());
                    GameEvent<List<Objective>> objectivesUpdatedEvent = new GameEvent<>(EventType.OBJECTIVES_UPDATED);
                    objectivesUpdatedEvent.setPayload(allActiveObjectives);
                    messagingService.sendToPlayer(player, GameServer.getGson().toJson(objectivesUpdatedEvent));
                } catch (Exception e) {
                    logger.error("Error while sending updated objectives to player {}: {}", player.getId(), e.getMessage());
                }
            }
        });
    }

    void checkGameOverConditions() {
        playerService.getPlayers().forEach((webSocket, player) -> {
            if (player.isBankrupt(lifeCycle.getLevel()) || player.completedAllObjectives()) {
                handleGameOver(player, webSocket); // Decide what to do next
            }
        });
    }

    private void handleGameOver(Player player, WebSocket webSocket) {
        int numberOfLevelsInTheGame = 3;
        int level = lifeCycle.getLevel();
        logger.debug("Game over for player {}.", player.getId());
        boolean playerHasWon = player.completedAllObjectives() && !player.isBankrupt(lifeCycle.getLevel());

        GameOverStats goStats = createGameOverStats(this, player, lifeCycle.getTick());
        goStats.setReport(playerHasWon ? "win" : "fail");

        if (playerHasWon) {
            // Keep the player in the game and prepare for the next level
            logger.debug("Player {} has completed all {} missions. Moving to next level ({}).",
                    player.hashCode(),
                    player.getMissions().size(),
                    level + 1);

            // Only increase level for existing levels
            if (level < numberOfLevelsInTheGame) {
                lifeCycle.setLevel(level + 1);
                logger.debug("Player {} has reached level {}.", player.getId(), lifeCycle.getLevel());
            } else {
                logger.debug("Player {} has reached the final level.", player.getId());
            }
        } else {
            logger.debug("Player {} has lost the game. Level stays the same. Try again! :)", player.getId());
        }

        // Send GAME_OVER event after decision
        GameEvent<GameOverStats> gameOverEvent = new GameEvent<>(EventType.GAME_OVER);
        gameOverEvent.setPayload(goStats);
        messagingService.sendToPlayer(player, gameOverEvent);

        // Update player one last time in this level to make sure, client is up-to-date
        GameEvent<Player> playerUpdateEvent = new GameEvent<>(EventType.PLAYER_UPDATED);
        playerUpdateEvent.setPayload(player);
        messagingService.sendToPlayer(player, playerUpdateEvent);

        // Send level updated
        GameEvent<Integer> levelUpdatedEvent = new GameEvent<>(EventType.LEVEL_UPDATED);
        levelUpdatedEvent.setPayload(lifeCycle.getLevel());
        messagingService.sendToPlayer(player, levelUpdatedEvent);

        // Fire game over event for GameServer to handle (save high-score etc.)
        GameOverData gameOverData = new GameOverData(webSocket, player, goStats, this);
        GameOverEvent internalGameOverEvent = new GameOverEvent(this, gameOverData);
        eventPublisher.publishGameOverEvent(internalGameOverEvent);

        // Let the Game class handle player removal
        removePlayer(webSocket);
    }

    private GameOverStats createGameOverStats(Game game, Player player, int tick) {
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
        goStats.setPlayedSeconds(tick * lifeCycle.getGameSpeedInMilliseconds() / 1000);
        goStats.setLevel(lifeCycle.getLevel());

        DecisionDAO dao = new DecisionDAO(DatabaseConfig.getDataSource());
        Map<Integer, List<OptionVoteDistribution>> distributions = null;
        try {
            distributions = dao.getVoteDistributionByLevel(lifeCycle.getLevel());
        } catch (ConnectException e) {
            logger.warn("Could not fetch vote distributions from database: {}", e.getMessage());
        }
        goStats.setCommunityVotes(distributions);

        return goStats;
    }

    public void removePlayer(Player player) {
        boolean removed = playerService.removePlayer(player);
        if (removed) {
            logger.debug("Player {} removed from game.", player.getId());
            closeGameIfNoPlayersLeft();
        } else {
            logger.warn("Player could not be removed from game.");
        }
    }

    public int getLevel() {
        return lifeCycle.getLevel();
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
        closeGameIfNoPlayersLeft();
    }

    public void closeGameIfNoPlayersLeft() {
        int numberOfPlayers = playerService.getPlayers().size();
        if (numberOfPlayers == 0) {
            logger.debug("I ({}) have no players left. Closing...", this.hashCode());
            shutdownGameLoop(gameLoop);

            // After game loop is shut down, fire event for GameServer to handle context clean-up, high-score etc.
            GlobalGameEmptyEvent globalGameEmptyEvent = new GlobalGameEmptyEvent(this, this);
            eventPublisher.publishGameEmptyEvent(globalGameEmptyEvent);
        } else {
            logger.debug("I ({}) have {} player(s). Staying alive.", this.hashCode(), numberOfPlayers);
        }
    }

    void shutdownGameLoop(ExecutorService pool) {
        if (pool == null) {
            return;
        }
        pool.shutdownNow();
    }

    // Forwarding the method for the GameServer
    public boolean isRunning() {
        return lifeCycle.isRunning();
    }
}
