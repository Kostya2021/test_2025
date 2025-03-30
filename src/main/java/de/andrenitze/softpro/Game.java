package de.andrenitze.softpro;

import de.andrenitze.softpro.config.DatabaseConfig;
import de.andrenitze.softpro.domains.GameOverStats;
import de.andrenitze.softpro.domains.decisions.DecisionDAO;
import de.andrenitze.softpro.domains.decisions.OptionVoteDistribution;
import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.employees.EmployeeIdGenerator;
import de.andrenitze.softpro.domains.objectives.Objective;
import de.andrenitze.softpro.domains.projects.Project;
import de.andrenitze.softpro.domains.projects.ProjectType;
import de.andrenitze.softpro.domains.story.StoryElement;
import de.andrenitze.softpro.domains.story.StoryElementsLoader;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static de.andrenitze.softpro.events.GameEventHandler.TEAM_SPIRIT;
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
    @Getter @Setter private GameEventHandler eventHandler;
    @Getter private GameEventPublisher eventPublisher;
    @Getter private ProjectEmployeeMappingImpl projectEmployeeService;
    @Setter @Getter private GameLifeCycleService lifeCycleService;
    @Setter private ObjectiveServiceImpl objectiveService;
    private LevelConsequencesService levelConsequencesService;

    public static final int GAME_SPEED_IN_MILLISECONDS = 200;
    public static final int NUMBER_OF_LEVELS_IN_THE_GAME = 3;

    @Getter private int tick = 0;
    @Getter private int level = 1;
    private ScheduledExecutorService gameLoop;
    private List<StoryElement> storyElements; // Level-specific

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
                LevelConsequencesService levelConsequencesService,
                GameLifeCycleService lifeCycleService,
                ProjectEmployeeMappingImpl projectEmployeeMapping) {
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
        this.levelConsequencesService = levelConsequencesService;
        this.lifeCycleService = lifeCycleService;
        this.projectEmployeeService = projectEmployeeMapping;

        // Don't initialize the talent market for level 1
        if (level != 1) {
            talentMarket.initialize();
        }
    }

    // Mandatory services are injected here
    public Game(GamePlayerServiceImpl playerService, SkillServiceImpl skillService, GameLifeCycleService lifeCycleService) {
        this.playerService = playerService;
        this.skillService = skillService;
        this.lifeCycleService = lifeCycleService;
    }

    public void start() {
        logger.debug("start()'ing game loop with {} players.", playerService.getPlayers().size());
        logger.debug("There are {} tenders in the game.", projectService.getProjects().size());

        // CHeck if there is at least one player in this game instance
        if (playerService.getPlayers().isEmpty()) {
            logger.error("No players in this game. Cannot start.");
            return;
        }

        // Start the round for all players
        lifeCycleService.run();
        messagingService.broadcastEvent(EventType.ROUND_STARTED);
        messagingService.broadcastInitialState();

        // Broadcast the initial state of the game to all players
        GameEvent<List<Project>> newTendersEvent = new GameEvent<>(EventType.TENDERS_ADDED);
        newTendersEvent.setPayload(projectService.getProjects());
        logger.debug("Sending {} tenders to players.", newTendersEvent.getPayload().size());
        messagingService.broadcastEvent(newTendersEvent);

        // Start running the game time
        gameLoop = Executors.newSingleThreadScheduledExecutor();
        gameLoop.scheduleWithFixedDelay(() -> {
            if (lifeCycleService.isPaused()) {
                return;
            }

            // Notify all clients of current time
            GameEvent<Integer> timerEvent = new GameEvent<>(EventType.TICK);
            timerEvent.setPayload(lifeCycleService.getTick());
            messagingService.broadcastEvent(timerEvent);

            progressGameTime();
        }, 750, GAME_SPEED_IN_MILLISECONDS, TimeUnit.MILLISECONDS);

        logger.info("A new game has started with {} players in level {}.", playerService.getPlayers().size(), getLevel());
    }

    /**
     * The next level is prepared, after players hit the "Start Level X" (PLAYER_READY) button.
     */
    public void prepareNextLevelForPlayer() {
        // Get next level from getPlayersService().getPlayers(). Highest level wins, but all players in one instance should have the same level.
        int nextLevel = 1;
        for (Player somePlayerInTheGame : playerService.getPlayers().values()) {
            if (somePlayerInTheGame.getLevel() > nextLevel) {
                nextLevel = somePlayerInTheGame.getLevel();
            }
        }
        setLevel(nextLevel);

        logger.debug("prepareNextLevel(): Players' highest level (= {}) will be the next level", nextLevel);

        if (getLevel() != 1) {
            projectService.initialize();

            // Send talent market to players at once
            GameEvent<List<Employee>> employeeEvent = new GameEvent<>(EventType.TALENTS_ADDED);
            employeeEvent.setPayload(talentMarket.getTalents());
            messagingService.broadcastEvent(employeeEvent);
        }

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
        playerService.getPlayers().forEach((_, somePlayerInTheGame) -> {
            logger.debug("Checking for permanent status effects for player {}", somePlayerInTheGame.getId());
            if (skillService.playerHasSkill(somePlayerInTheGame, TEAM_SPIRIT)) {
                logger.debug("Player {} has the skill {}", somePlayerInTheGame.getId(), TEAM_SPIRIT);
                somePlayerInTheGame.getEmployees().forEach(employee -> {
                    logger.debug("Adding permanent status effect {} to employee {}", TEAM_SPIRIT, employee.getId());
                    employee.addComplexStatusEffect(TEAM_SPIRIT);
                });
            }
        });

        playerService.getPlayers().forEach((_, somePlayerInTheGame ) -> {
            if (somePlayerInTheGame.getDecisionsByLevel(getLevel()).isEmpty()) {
                logger.warn("Player {} has no decisions for level {}", somePlayerInTheGame.getId(), getLevel());
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
        if (lifeCycleService.isPaused()) {
            return;
        }

        lifeCycleService.nextTick();

        // Progress calendar date
        currentDate = now();
        currentDate = currentDate.plusDays(lifeCycleService.getTick());

        long startTime = System.nanoTime();
        // Execute the game logic each "tick".
        // This is important because player interactions alter the state between ticks.
        // Order is important, because some methods depend on the state of others (side effects may occur).
        projectService.conductWorkOnAllProjects(lifeCycleService.getTick(), getLevel(), currentDate);
        projectService.cancelOverdueProjects(lifeCycleService.getTick(), getLevel());
        projectService.evaluateTenderProcesses(lifeCycleService.getTick());
        // Things not to do in the first level
        // The "level" param in the other methods are used for a similar decision and might be removed in the future.
        if (getLevel() != 1) {
            projectService.randomlySpawnProjectTenders(lifeCycleService.getTick(), getLevel(), playerService.getRandomPlayer());
            projectService.generateRandomComplianceProjects(lifeCycleService.getTick(), playerService.getRandomPlayer());
            projectService.removeStaleTenders(lifeCycleService.getTick());
            projectService.startStaleProjects(lifeCycleService.getTick());
        }
        projectService.createProblemsInProjects(lifeCycleService.getTick(), getLevel());
        accountingService.processMonthlyPayments(currentDate, playerService.getPlayers(), lifeCycleService.getTick(), getLevel());
        messagingService.sendNewAccountingEntries(accountingService.getNewAccountingEntries(lifeCycleService.getTick()));
        employeeService.simulateEmployeeLives(lifeCycleService.getTick());

        Map<Player, List<Objective>> newObjectivesMap = objectiveService.getNewObjectives(lifeCycleService.getTick());
        for (Map.Entry<Player, List<Objective>> entry : newObjectivesMap.entrySet()) {
            Player player = entry.getKey();
            List<Objective> allObjectives = player.getObjectivesUntilThisTick(lifeCycleService.getTick());
            GameEvent<List<Objective>> gameEvent = new GameEvent<>(EventType.OBJECTIVES_UPDATED);
            gameEvent.setPayload(allObjectives);
            messagingService.sendEventToPlayer(player, gameEvent);
        }
        checkObjectivesCriteriaAndSendRewards();
        sendStoryElements();
        checkGameOverConditions();

        long endTime = System.nanoTime();
        long timeElapsedInMilliseconds = (endTime - startTime) / 1000000;

        // If time elapsed is more than 20 ms and game is not currently shutting down
        if (timeElapsedInMilliseconds >= 20 && lifeCycleService.isRunning()) {
            logger.warn("Execution time of game loop: {} ms", timeElapsedInMilliseconds);
        }
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
            if (!element.isSent() && (element.getEarliestOccurrence() == lifeCycleService.getTick() ||
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
        playerService.getPlayers().forEach((_, player) -> {
            if (objectiveService.areThereObjectivesUpdates(player)) {
                logger.debug("Sending updated objectives to player.");
                List<Objective> allActiveObjectives = player.getObjectivesUntilThisTick(lifeCycleService.getTick());
                GameEvent<List<Objective>> objectivesUpdatedEvent = new GameEvent<>(EventType.OBJECTIVES_UPDATED);
                objectivesUpdatedEvent.setPayload(allActiveObjectives);
                messagingService.sendMessageToPlayer(player, GameServer.getGson().toJson(objectivesUpdatedEvent));
            }
        });
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

        GameOverStats goStats = createGameOverStats(this, player, lifeCycleService.getTick());
        goStats.setReport(playerHasWon ? "win" : "fail");

        if (playerHasWon) {
            // Keep the player in the game and prepare for the next level
            logger.debug("Player {} has completed all {} missions. Moving to next level ({}).",
                    player.hashCode(),
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

    public void removePlayer(Player player) {
        boolean removed = playerService.removePlayer(player);
        if (removed) {
            logger.debug("Player {} removed from game.", player.getId());
            closeGameIfNoPlayersLeft();
        } else {
            logger.warn("Player could not be removed from game.");
        }
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

    public void addPlayerToGame(WebSocket webSocket, Player player) {
        logger.debug("Adding player {} to game.", player.getId());
        try {
            if (playerService.hasWebSocket(webSocket)) {
                logger.warn("Player already exists in game. Ignoring request to add player.");
                return;
            } else if (playerService.getPlayers().size() >= MAX_NUMBER_OF_PLAYERS_PER_GAME) {
                logger.warn("Game is full. Cannot add player.");
                return;
            } else if (playerService.isPlayerInAnyGame(player)) {
                logger.warn("Player is already in another game. Cannot add player.");
                return;
            }

            playerService.addPlayer(webSocket, player);
            logger.debug("Added player {} to game.", player.getId());

            PlayersChangedEvent playersChangedEvent = new PlayersChangedEvent(this, playerService.getPlayers());
            eventPublisher.publishPlayersChangedEvent(playersChangedEvent);
        } catch (Exception e) {
            logger.error("Could not add player to game: {}", e.getMessage());
        }
    }

    // Forwarding the method for the GameServer
    public boolean isRunning() {
        return lifeCycleService.isRunning();
    }
}
