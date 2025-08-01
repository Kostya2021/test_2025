package de.andrenitze.softpro;

import de.andrenitze.softpro.domains.GameOverStats;
import de.andrenitze.softpro.domains.GameState;
import de.andrenitze.softpro.domains.decisions.DecisionDAO;
import de.andrenitze.softpro.domains.decisions.OptionVoteDistribution;
import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.employees.TalentMarket;
import de.andrenitze.softpro.domains.objectives.Objective;
import de.andrenitze.softpro.domains.players.Player;
import de.andrenitze.softpro.domains.projects.Project;
import de.andrenitze.softpro.domains.projects.ProjectSummary;
import de.andrenitze.softpro.domains.projects.ProjectType;
import de.andrenitze.softpro.domains.skills.Skill;
import de.andrenitze.softpro.domains.story.StoryElement;
import de.andrenitze.softpro.events.*;
import de.andrenitze.softpro.services.*;
import de.andrenitze.softpro.services.impl.*;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.WebSocket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static de.andrenitze.softpro.services.impl.StatusEffectService.TEAM_SPIRIT;
import static de.andrenitze.softpro.services.impl.player.GamePlayerServiceImpl.MAX_NUMBER_OF_PLAYERS_PER_GAME;

@Slf4j
@Getter
@RequiredArgsConstructor
public class Game {
    private final StoryService                  storyService;
    private final GamePlayerService playerService;
    private final SkillService skillService;
    private final AccountingService accountingService;
    private final MessagingService              messagingService;
    private final TalentMarket                  talentMarket;
    private final ProjectService                projectService;
    private final EmployeeService employeeService;
    private final GameEventHandler eventHandler;
    private final GameEventPublisher            eventPublisher;
    private final ProjectEmployeeMappingService projectEmployeeService;
    private final GameLifeCycleService          lifeCycle;
    private final LevelConsequencesService      levelConsequencesService;
    private final ObjectiveService objectiveService;
    private final DecisionDAO                   decisionDAO;
    private final StatusEffectService statusEffectService;

    private ScheduledExecutorService gameLoop;

    @PostConstruct
    void init() {
        log.debug("Game {} wired", hashCode());
    }

    public void start() {
        log.debug("start()'ing game loop with {} players.", playerService.getPlayers().size());

        // CHeck if there is at least one player in this game instance
        if (playerService.getPlayers().isEmpty()) {
            log.error("No players in this game. Cannot start.");
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

        log.info("A new game has started with {} players in level {}.", playerService.getPlayers().size(), lifeCycle.getLevel());
    }

    /**
     * Execute the game logic each "tick". Player interactions can alter the state between ticks.
     * Order is important, because some methods depend on the state of others (side effects are likely).
     */
    private void progressGameTime() {
        try {
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
            messagingService.sendNewAccountingEntries(accountingService.getAccountingEntriesByTick(lifeCycle.getTick(), playerService.getPlayers()));
            employeeService.simulateEmployeeLives(lifeCycle.getTick(), playerService.getPlayers());

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
                                log.debug("New compliance project spawned for all players: {}", project.getName());
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
                sendPendingProjectChanges();
                sendPendingPlayerChanges(player);
                sendPendingStoryElements(player);
                sendPendingEmployeeUpdates(player);
            });

            checkGameOverConditions();

            long endTime = System.nanoTime();
            long timeElapsedInMilliseconds = (endTime - startTime) / 1000000;

            // If time elapsed is more than 20 ms and game is not currently shutting down
            if (timeElapsedInMilliseconds >= 20 && lifeCycle.isRunning()) {
                log.warn("Execution time of game loop: {} ms", timeElapsedInMilliseconds);
            }
        } catch (Exception e) {
            log.error("Error in game loop: {}", e.getMessage());
        }
    }

    /**
     * The next level is prepared after players hit the "Start Level X" (PLAYER_READY) button.
     * This is important, because player decisions are needed to trigger the level consequences.
     */
    void triggerConsequencesForDecisions() {
        log.debug("Preparing level {} for player.", lifeCycle.getLevel());

        // Trigger level consequences
        switch (lifeCycle.getLevel()) {
            case 1 -> levelConsequencesService.triggerLevel1Consequences();
            case 2 -> levelConsequencesService.triggerLevel2Consequences();
            case 3 -> levelConsequencesService.triggerLevel3Consequences();
            case MAX_NUMBER_OF_PLAYERS_PER_GAME -> levelConsequencesService.triggerLevel4Consequences();
            default -> log.warn("No level consequences for level {}", lifeCycle.getLevel());
        }

        // Add permanent status effects to players
        playerService.getPlayers().forEach((ignored, somePlayerInTheGame) -> {
            log.debug("Checking for permanent status effects for player {}", somePlayerInTheGame.getId());
            if (skillService.playerHasSkill(somePlayerInTheGame, TEAM_SPIRIT)) {
                log.debug("Player {} has the skill {}", somePlayerInTheGame.getId(), TEAM_SPIRIT);
                somePlayerInTheGame.getEmployees().forEach(employee -> {
                    log.debug("Adding permanent status effect {} to employee {}", TEAM_SPIRIT, employee.getId());
                    //employee.addComplexStatusEffect(TEAM_SPIRIT); было так
                    statusEffectService.addComplexStatusEffect(employee, TEAM_SPIRIT);
                });
            }
        });
    }

    // Call this method after creating the game instance and before starting the game loop.
    public void initialize(int level) {
        log.debug("Initializing level {}...", level);
        lifeCycle.setLevel(level);

        projectService.loadProblems(level);
        storyService.loadStory(level);

        playerService.getPlayers().forEach((webSocket, player) -> skillService.initializePlayer(player));

        if (level > 1) {
            projectService.initialize(level);
            talentMarket.initialize();

            GameEvent<List<Employee>> employeeEvent = new GameEvent<>(EventType.TALENTS_ADDED);
            employeeEvent.setPayload(talentMarket.getTalents());
            messagingService.broadcast(employeeEvent);
        }
    }

    private void sendPendingStoryElements(Player player) {
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
    private void sendPendingProjectChanges() {
        playerService.getPlayers().forEach((ignored, player) -> {
            List<Project> currentProjects = projectService.getProjectsByPlayer(player);

            for (Project project : currentProjects) {
                Project previousState = projectService.getPreviousState(project.getId());

                if (previousState == null || project.hasChanged(previousState)) {
                    log.debug("Project '{}' has changed since last tick. Sending new state.", project.getName());

                    try {
                        GameEvent<Project> projectUpdateEvent = new GameEvent<>(EventType.PROJECT_UPDATED);
                        projectUpdateEvent.setPayload(project);
                        messagingService.sendToPlayer(player, projectUpdateEvent);

                        projectService.updatePreviousState(project);
                    } catch (Exception e) {
                        log.error("Error while sending project update: {}", e.getMessage());
                    }
                }
            }
        });
    }

    private void sendPendingPlayerChanges(Player player) {
        try {
            Player previousState = playerService.getPreviousState(player);

            if (previousState == null || player.hasChanged(previousState)) {
                log.debug("Player '{}' has changed since last tick. Sending new state.", player.getId());

                GameEvent<Player> playerUpdateEvent = new GameEvent<>(EventType.PLAYER_UPDATED);
                playerUpdateEvent.setPayload(player);
                messagingService.sendToPlayer(player, playerUpdateEvent);

                // Save a copy of the current state for future comparison
                playerService.updatePreviousState(player);
            }
        } catch (Exception e) {
            log.error("Error while sending player update: {}", e.getMessage());
        }
    }

    public void sendPendingEmployeeUpdates(Player player) {
        player.getEmployees().stream()
                .filter(Employee::isUpdated)
                .forEach(employee -> {
                    messagingService.sendEmployeeUpdate(player, employee);
                    employee.setUpdated(false);
                });
    }

    private void checkObjectivesCriteriaAndSendRewards() {
        playerService.getPlayers().forEach((ignored, player) -> {
            if (objectiveService.areThereObjectivesUpdates(player)) {
                log.debug("Sending updated objectives to player.");
                try {
                    List<Objective> allActiveObjectives = player.getObjectivesUntilThisTick(lifeCycle.getTick());
                    GameEvent<List<Objective>> objectivesUpdatedEvent = new GameEvent<>(EventType.OBJECTIVES_UPDATED);
                    objectivesUpdatedEvent.setPayload(allActiveObjectives);
                    messagingService.sendToPlayer(player, GameServer.getGson().toJson(objectivesUpdatedEvent));
                } catch (Exception e) {
                    log.error("Error while sending updated objectives to player {}: {}", player.getId(), e.getMessage());
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
        lifeCycle.pause();
        int numberOfLevelsInTheGame = 3;
        int level = lifeCycle.getLevel();
        log.debug("Game over for player {} at level {}.", player.getId(), level);

        boolean playerHasWon = player.completedAllObjectives() && !player.isBankrupt(level);

        GameOverStats goStats = createGameOverStats(this, player, lifeCycle.getTick());
        goStats.setReport(playerHasWon ? "win" : "fail");
        goStats.setLevel(level);
        goStats.setScore(new ScoreCalculator().calculateScore(player, this));

        GameEvent<GameOverStats> gameOverEvent = new GameEvent<>(EventType.GAME_OVER);
        gameOverEvent.setPayload(goStats);
        messagingService.sendToPlayer(player, gameOverEvent);

        GameEvent<Player> playerUpdateEvent = new GameEvent<>(EventType.PLAYER_UPDATED);
        playerUpdateEvent.setPayload(player);
        messagingService.sendToPlayer(player, playerUpdateEvent);

        if (playerHasWon) {
            if (level < numberOfLevelsInTheGame) {
                lifeCycle.setLevel(level + 1);
                log.debug("Player {} has now reached level {}.", player.getId(), lifeCycle.getLevel());
            } else {
                log.debug("Player {} has reached the final level.", player.getId());
            }
        } else {
            log.debug("Player {} has lost. Level stays at {}. Try again! :)", player.getId(), level);
        }

        // In any case, the game is over for this player. Notify the game server.
        GameOverData gameOverData = new GameOverData(webSocket, player, goStats, this);
        GameOverEvent internalGameOverEvent = new GameOverEvent(this, gameOverData);
        eventPublisher.publishGameOverEvent(internalGameOverEvent);
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

        Map<Integer, List<OptionVoteDistribution>> distributions = null;
        try {
            distributions = decisionDAO.getVoteDistributionByLevel(lifeCycle.getLevel());
        } catch (Exception e) {
            log.warn("Could not fetch vote distributions from database: {}", e.getMessage());
        }
        goStats.setCommunityVotes(distributions);

        return goStats;
    }

    public boolean removePlayer(Player player) {
        boolean removed = playerService.removePlayer(player);
        if (removed) {
            log.debug("Player {} removed from game with now {} players.", player.getId(), playerService.getPlayers().size());
            closeGameIfNoPlayersLeft();
        } else {
            log.warn("Player could not be removed from game.");
        }
        return removed;
    }

    public int getLevel() {
        return lifeCycle.getLevel();
    }

    public void restoreGame(GameState savedGame, Player player) {
        if (player == null || savedGame.getPlayer() == null) {
            log.error("Player or game state is null. Cannot restore game state.");
            return;
        }

        if (!Objects.equals(player.getJwtSubject(), savedGame.getPlayer().getJwtSubject())) {
            log.error("Player subject mismatch. Cannot restore game state.");
            return;
        }

        // Restore game state
        // Set level to the next one
        this.lifeCycle.setLevel(savedGame.getLevel()+1);

        // Restore player state
        player.setName(savedGame.getPlayer().getName());
        player.setCompany(savedGame.getPlayer().getCompany());
        player.setXp(savedGame.getPlayer().getXp());
        player.setXpLevel(savedGame.getPlayer().getXpLevel());
        player.setSkillPoints(savedGame.getPlayer().getSkillPoints());

        // Forget first level employees
        if (savedGame.getLevel() != 1 && savedGame.getPlayer().getEmployees() != null) {
            player.setEmployees(savedGame.getPlayer().getEmployees());
        }

        // Not getPlayer().getDecisions()! Decisions are saved separately in the GameState
        if (savedGame.getDecisions() != null) {
            for (var entry : savedGame.getDecisions().entrySet()) {
                player.setDecisionsForLevel(entry.getKey(), entry.getValue());
            }
        }

        if (savedGame.getSkills() != null) {
            skillService.setSkills(player, savedGame.getSkills());
        }

        log.debug("Game state successfully restored for player {}.", player.getId());
    }

    public GameState exportState(Player player) {
        GameState gameState = new GameState();

        gameState.setLevel(getLevel());
        gameState.setPlayer(player);
        gameState.setAccountingEntries(accountingService.getAllEntriesByPlayer(player));
        gameState.setProjects(projectService.getProjectsByPlayer(player));
        gameState.setSkills((HashMap<String, Skill>) skillService.getSkillsByPlayer(player));
        gameState.setDecisions(player.getDecisions());

        return gameState;
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

    public void closeGameIfNoPlayersLeft() {
        int numberOfPlayers = playerService.getPlayers().size();
        if (numberOfPlayers == 0) {
            log.debug("Game {} has no players left. Closing...", this.hashCode());
            shutdownGameLoop(gameLoop);

            // After game loop is shut down, fire event for GameServer to handle context clean-up, high-score etc.
            GlobalGameEmptyEvent globalGameEmptyEvent = new GlobalGameEmptyEvent(this, this);
            eventPublisher.publishGameEmptyEvent(globalGameEmptyEvent);
        } else {
            log.debug("Game {} has {} player(s). Staying alive.", this.hashCode(), numberOfPlayers);
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
