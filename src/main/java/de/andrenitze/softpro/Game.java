package de.andrenitze.softpro;

import de.andrenitze.softpro.domains.*;
import de.andrenitze.softpro.domains.accounting.AccountCategory;
import de.andrenitze.softpro.domains.accounting.AccountingEntry;
import de.andrenitze.softpro.domains.accounting.AccountingService;
import de.andrenitze.softpro.domains.accounting.TransactionType;
import de.andrenitze.softpro.domains.decisions.DecisionDAO;
import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.employees.EmployeeIdGenerator;
import de.andrenitze.softpro.domains.objectives.Mission;
import de.andrenitze.softpro.domains.objectives.Objective;
import de.andrenitze.softpro.domains.objectives.ObjectiveChecker;
import de.andrenitze.softpro.domains.projects.Problem;
import de.andrenitze.softpro.domains.projects.ProblemGenerator;
import de.andrenitze.softpro.domains.story.StoryElement;
import de.andrenitze.softpro.domains.story.StoryElementsLoader;
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
import java.util.stream.Collectors;

import static de.andrenitze.softpro.GameEventHandler.TEAM_SPIRIT;
import static de.andrenitze.softpro.GameServer.GSON;
import static de.andrenitze.softpro.GameServer.RANDOM;
import static de.andrenitze.softpro.types.ProjectType.COMPLIANCE_PROJECT_NAMES;
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
    public static final String FAMILIARIZATION_WITH_NEW_DOMAIN = "Familiarization with new project domain";
    private static final String FAMILIARIZATION_WITH_NEW_TYPE = "Familiarization with new project type";
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
    @Getter
    protected final ConcurrentHashMap<Project, ArrayList<Employee>> projectEmployeesMap = new ConcurrentHashMap<>();
    private ArrayList<StoryElement> storyElements; // Level-specific
    @Getter
    private final SkillsManager skillsManager;
    @Getter
    private final TalentMarket talentMarket;
    @Getter
    private int level = 1;
    @Getter
    private final ProblemGenerator problemGenerator = new ProblemGenerator();
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
        messagingService.broadcastToAllPlayers(GSON.toJson(startEvent));

        // Send initial state to all players
        players.forEach((webSocket, player) -> {
            // Initialize skills
            skillsManager.addPlayer(player);

            // Send state
            logger.debug("Sending initial state to players");
            GameEvent<Player> initialPlayerEvent = new GameEvent<>(EventType.STATE_UPDATED);
            initialPlayerEvent.setPayload(player);
            messagingService.sendMessageToPlayer(player, GSON.toJson(initialPlayerEvent));
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
    protected void prepareNextLevel() {
        // Get next level from players. Highest level wins, but all players in one instance should have the same level.
        int nextLevel = 0;
        for (Player player : players.values()) {
            if (player.getLevel() > nextLevel) {
                nextLevel = player.getLevel();
            }
        }
        setLevel(nextLevel);
        problemGenerator.loadProblemsByLevel(getLevel());

        logger.debug("prepareNextLevel(): Players's highest level (= {}) will be the next level", nextLevel);

        if (getLevel() != 1) {
            projectService.setProjects(new ArrayList<>());
            for (int i = 0; i < 100; i++) {
                Project project = new Project().initialize();

                // Set randomly negative publish dates to have some history of tenders
                project.setPublishedAt((int) round(Math.random() * STALE_TENDERS_KILL_DAYS * -1));

                projectService.addProject(project);
                projectEmployeesMap.put(project, new ArrayList<>());
            }

            // Send talent market to players at once
            GameEvent<ArrayList<Employee>> employeeEvent = new GameEvent<>(EventType.TALENTS_ADDED);
            employeeEvent.setPayload(talentMarket.getTalents());
            messagingService.broadcastToAllPlayers(GSON.toJson(employeeEvent));
        }

        // Send all tenders at once
        GameEvent<ArrayList<Project>> projectEvent = new GameEvent<>(EventType.TENDERS_ADDED);
        projectEvent.setPayload(projectService.getProjects());
        messagingService.broadcastToAllPlayers(GSON.toJson(projectEvent));

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
        players.forEach((webSocket, player) -> {
            logger.debug("Checking for permanent status effects for player {}", player.getId());
            if (skillsManager.playerHasSkill(player, TEAM_SPIRIT)) {
                logger.debug("Player {} has the skill {}", player.getId(), TEAM_SPIRIT);
                player.getEmployees().forEach(employee -> {
                    logger.debug("Adding permanent status effect {} to employee {}", TEAM_SPIRIT, employee.getId());
                    employee.addComplexStatusEffect(TEAM_SPIRIT);
                });
            }
        });

        players.forEach((webSocket, player) -> {
            if (player.getDecisionsByLevel(getLevel()).isEmpty()) {
                logger.warn("Player {} has no decisions for level {}", player.getId(), getLevel());
            }
        });
    }


    private void triggerLevel1Consequences() {
        players.forEach((webSocket, player) -> {
            // Decision "Fail to plan, plan to fail" (level 1, decision 1)
            int option = player.getDecisionsByLevel(1).get(0).getOptionId();

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
        players.forEach((webSocket, player) -> {
            // "Backup decision" (level 2, decision 1)
            int option = player.getDecisionsByLevel(BACKUP_BLUES_LEVEL).get(0).getOptionId();
            if (option == 1) {
                // Option 1 "Employee does it": Lower productivity of first employee as status effect for the whole level
                // Make sure that the effect stays even if employee is fired. Always use the first employee.
                player.setFunds(player.getFunds() - 5000);
                Employee firstEmployee = player.getEmployees().get(0);
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
        players.forEach((webSocket, player) -> {
            // "Backup decision" (level 2, decision 1)
            int backupOption = player.getDecisionsByLevel(BACKUP_BLUES_LEVEL).get(0).getOptionId();
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
        players.forEach((webSocket, player) -> {
            // "Backup decision"
            int backupOption = player.getDecisionsByLevel(BACKUP_BLUES_LEVEL).get(0).getOptionId();
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
        projectService.conductWorkOnAllProjects(currentTick, currentDate, projectEmployeesMap);
        projectService.cancelOverdueProjects(currentTick);
        accountingService.processMonthlyPaymentsPerTick(currentDate, players, currentTick);
        randomlySpawnProjectTendersPerTick();
        randomlySpawnComplianceProjectsPerTick();
        removeStaleTendersPerTick();
        evaluateTenderProcessesPerTick();
        sendNewObjectivesPerTick();
        checkObjectivesCriteriaAndSendRewardsPerTick();
        simulateEmployeeLivesPerTick();
        sendStoryElementsPerTick();
        startStaleProjectsPerTick();
        createProblemsInProjectsPerTick();
        sendNewAccountingEntriesPerTick();
        checkGameOverConditionsPerTick();

        long endTime = System.nanoTime();
        long timeElapsedInMilliseconds = (endTime - startTime) / 1000000;

        if (timeElapsedInMilliseconds >= 20) {
            logger.warn("Execution time of game loop: {} ms", timeElapsedInMilliseconds);
        }
    }

    private void sendNewAccountingEntriesPerTick() {
        // Send new accounting entries (the ones with tick == currentTick) to the corresponding players
        players.forEach((webSocket, player) -> {
            List<AccountingEntry> newEntries = accountingService.getAllEntriesByPlayer(player.getId()).stream()
                    .filter(entry -> entry.getDay() == currentTick)
                    .collect(Collectors.toList());

            if (!newEntries.isEmpty()) {
                GameEvent<List<AccountingEntry>> newAccountingEntriesEvent = new GameEvent<>(EventType.ACCOUNTING_ENTRIES_ADDED);
                newAccountingEntriesEvent.setPayload(newEntries);
                messagingService.sendMessageToPlayer(player, GSON.toJson(newAccountingEntriesEvent));
            }
        });
    }

    private void randomlySpawnComplianceProjectsPerTick() {
        // Don't auto-spawn compliance projects in level 1
        if (getLevel() == 1) {
            return;
        }

        Player player = players.values().iterator().next();

        // Only have one compliance project at a time
        if (projectService.getProjects().stream().noneMatch(project -> project.getType() == ProjectType.COMPLIANCE) &&
                RANDOM.nextFloat() <= COMPLIANCE_PROJECT_SPAWN_PROBABILITY) {
            // Generate a new compliance project
            Project project = new Project(ProjectType.COMPLIANCE, "Compliance", RiskLevel.low, false);

            project.setPublishedAt(getCurrentTick());
            project.setAcquiredAt(getCurrentTick()); // Immediately acquired: Frontend will show it as "acquired"
            project.addParty(player); // Add the player as involved party (also important for frontend)
            project.setDeadline(0);
            // Select a name from a list of predefined names
            project.setName(COMPLIANCE_PROJECT_NAMES.get(RANDOM.nextInt(COMPLIANCE_PROJECT_NAMES.size())));
            projectService.getProjects().add(project);

            // Add to project-employee map
            projectEmployeesMap.put(project, new ArrayList<>());

            // Immediately assign the project to all players
            GameEvent<Project> newProjectEvent = new GameEvent<>(EventType.PROJECT_RECEIVED);
            newProjectEvent.setPayload(project);
            logger.debug("New compliance project spawned for all players: {}", project.getName());
            messagingService.broadcastToAllPlayers(GSON.toJson(newProjectEvent));
        }
    }

    private void createProblemsInProjectsPerTick() {
        // In all running projectService.getProjects()...
        for (Project project : projectService.getProjects()) {
            // If it's not running, don't create problems
            if (project.getStartedAt() == 0 || project.isCompleted()) {
                continue;
            }

            // For now, with a fixed chance for a problem to occur,
            // (can be adjusted later depending on project volume, risk level, etc.)
            double problemSpawnProbability = 0.01;
            int maxUnsolvedProblemsPerProject = level; // In higher levels, more problems can occur

            // but not more than a certain number problems per project
            if (RANDOM.nextFloat() <= problemSpawnProbability
                    && project.getUnsolvedProblems().size() < maxUnsolvedProblemsPerProject) {
                // Take all problems of the project
                List<Problem> occurredProblems = project.getProblems();

                // Create a problem that has not occurred in the project before (independent of resolution state)
                Problem problem = problemGenerator.generateRandomNewProblem(occurredProblems);
                if (problem == null) { // All problems have occurred
                    continue;
                }

                // Add the problem to the project
                problem.setOccurredAt(currentTick);
                project.addProblem(problem);

                // Inform all involved players about the new problem
                logger.debug("New problem in project {} ({}): {}", project.getId(), project.getName(), problem.getTranslationKey());

                project.getInvolvedPlayers().forEach(player -> {
                    GameEvent<Project> projectUpdatedEvent = new GameEvent<>(EventType.PROJECT_UPDATED);
                    projectUpdatedEvent.setPayload(project);
                    messagingService.sendMessageToPlayer(player, GSON.toJson(projectUpdatedEvent));
                });
            }
        }
    }

    private void startStaleProjectsPerTick() {
        // Don't do that in level 1
        if (getLevel() == 1) {
            return;
        }

        // Don't do it for COMPLIANCE projects, independent of startedAt, acquiredAt etc.
        for (Project project : getProjectService().getProjects()) {
            if (project.getType() == ProjectType.COMPLIANCE) {
                continue;
            }

            // For all projects that have been acquired, but not started after MAX(30 days, 10% of project duration)
            if (project.getAcquiredAt() != 0 && project.getStartedAt() == 0) {
                int daysPassed = currentTick - project.getAcquiredAt();
                if (daysPassed >= Math.max(30, project.getScheduledDuration() / 10)) {
                    // Start the project and inform involved players
                    projectService.startProject(project, currentTick - 1);
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
        project.getInvolvedPlayers().forEach(player -> messagingService.sendMessageToPlayer(player, GSON.toJson(projectStartedEvent)));
    }

    private void removeStaleTendersPerTick() {
        // Dont remove tenders in level 1
        if (getLevel() == 1) {
            return;
        }

        // Remove tenders that have been on the market for a long time and store them in a separate array
        List<Project> staleTenders = new ArrayList<>();
        for (Iterator<Project> iterator = projectService.getProjects().iterator(); iterator.hasNext();) {
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
        messagingService.broadcastToAllPlayers(GSON.toJson(tendersRemovedEvent));
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

            if (!thisPlayersStoryElements.isEmpty()) {
                // Send the compiled list to the player
                newStoryElementEvent.setPayload(thisPlayersStoryElements);
                messagingService.sendMessageToPlayer(player, GSON.toJson(newStoryElementEvent));
            }
        });
    }

    private void simulateEmployeeLivesPerTick() {
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
            applyStatusEffectsForStressfulOnboarding(player, employee);

            // Send an employee update, if anything has changed
            if (needsUpdate) {
                sendEmployeeUpdate(player, employee);
            }
        }));
    }

    public void applyStatusEffectsForStressfulOnboarding(Player player, Employee employee) {
        // If employee is assigned to a project, in which he/she has low experience, satisfaction decreases (status effect)
        if (projectEmployeesMap.values().stream().anyMatch(employees -> employees.contains(employee))) {
            projectEmployeesMap.forEach((project, employees) -> {
                // Make sure project has started to prevent status effect from being applied
                if (project.getStartedAt() == 0) {
                    return;
                }

                // New project type decreases satisfaction by 30%
                StatusEffect newProjectTypeEffect = new StatusEffect(StatusEffectType.SATISFACTION,
                        0.7f,
                        FAMILIARIZATION_WITH_NEW_TYPE);

                // New project domain decreases satisfaction by 15%
                StatusEffect newProjectDomainEffect = new StatusEffect(StatusEffectType.SATISFACTION,
                        0.85f,
                        FAMILIARIZATION_WITH_NEW_DOMAIN);

                // Make sure it's only applied once
                if (employees.contains(employee) && !employee.getStatusEffects().contains(newProjectTypeEffect)) {
                    if (employee.getExperienceByType(project.getType()) < DAYS_TO_LEARN_NEW_THINGS) {
                        employee.addStatusEffect(newProjectTypeEffect);
                    }
                }

                if (employees.contains(employee) && !employee.getStatusEffects().contains(newProjectDomainEffect)) {
                    if (employee.getExperienceByDomain(project.getDomain()) < DAYS_TO_LEARN_NEW_THINGS) {
                        employee.addStatusEffect(newProjectDomainEffect);
                    }
                }

                // Notify frontend about status effect
                sendEmployeeUpdate(player, employee);
            });
        }
    }

    public void sendEmployeeUpdate(Player player, Employee employee) {
        GameEvent<Employee> employeeUpdateEvent = new GameEvent<>(EventType.EMPLOYEE_UPDATED);
        employeeUpdateEvent.setPayload(employee);
        messagingService.sendMessageToPlayer(player, GSON.toJson(employeeUpdateEvent));
    }

    private void checkObjectivesCriteriaAndSendRewardsPerTick() {
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



    void checkGameOverConditionsPerTick() {
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
        messagingService.sendMessageToPlayer(player, GSON.toJson(gameOverEvent));
        logger.debug("Sent GAME_OVER event to player: {}", GSON.toJson(gameOverEvent));

        // Update player one last time in this level to make sure, client is up-to-date
        GameEvent<Player> playerUpdateEvent = new GameEvent<>();
        playerUpdateEvent.setType(EventType.PLAYER_UPDATED);
        playerUpdateEvent.setPayload(player);
        webSocket.send(GSON.toJson(playerUpdateEvent));

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
    void sendNewObjectivesPerTick() {
        players.forEach((webSocket, player) -> {
            boolean thereAreNewObjectives = !player.getNewObjectivesByTick(getCurrentTick()).isEmpty();
            if (thereAreNewObjectives) {
                logger.debug("Sending {} new objectives to player.", player.getNewObjectivesByTick(getCurrentTick()).size());

                GameEvent<List<Objective>> objectivesUpdatedEvent = new GameEvent<>(EventType.OBJECTIVES_UPDATED);

                // For the frontend, still include ALL objectives, even completed ones, in this event
                List<Objective> allObjectives = player.getObjectivesUntilThisTick(getCurrentTick());
                objectivesUpdatedEvent.setPayload(allObjectives);
                messagingService.sendMessageToPlayer(player, GSON.toJson(objectivesUpdatedEvent));
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

    private void randomlySpawnProjectTendersPerTick() {
        // There is only one player in level 1
        Player p = players.values().iterator().next();

        // Don't spawn new projects in level 1 before the first mission is completed
        if (getLevel() == 1 && p.getMissions().get(0).isNotCompleted()) {
            return;
        }

        if (RANDOM.nextFloat() <= PROJECT_SPAWN_PROBABILITY) {
            // Generate a new project
            Project project;

            // For level 1, make sure that it's only easy and small projects
            if (getLevel() == 1) {
                // 25% chance for a perfect project
                if (RANDOM.nextFloat() <= 0.75) {
                    // Low-risk, small projects
                    project = new Project(RiskLevel.low).initialize();

                    // No tender process for level 1
                    project.setTenderProcess(false);
                } else {
                    // Find the project type and domain where one employee has the most experience
                    Employee bestEmployee = p.getEmployees().stream().max(Comparator.
                            comparing(Employee::getExperience)).orElse(null);
                    if (bestEmployee == null) {
                        logger.error("No employee found for player {}.", p.getId());
                        return;
                    }
                    String domain = bestEmployee.getDomainOfExpertise();
                    ProjectType type = ProjectType.getTypeByDomain(domain);

                    project = new Project(type, domain, RiskLevel.low, false);
                }
            } else {
                project = new Project().initialize();
            }

            project.setPublishedAt(getCurrentTick());
            projectService.getProjects().add(project);

            // Initialize project-employee map
            projectEmployeesMap.put(project, new ArrayList<>(2));

            // Inform players about the new tender
            GameEvent<Project> newTenderEvent = new GameEvent<>(EventType.NEW_TENDER);
            newTenderEvent.setPayload(project);

            messagingService.broadcastToAllPlayers(GSON.toJson(newTenderEvent));
        }
    }

    private void evaluateTenderProcessesPerTick() {
        for (Project project : getProjectService().getProjects()) {
            // Don't evaluate acquired projects
            if (project.getAcquiredAt() != 0) {
                continue;
            }

            // Regular case: No tender process, assign project immediately
            if (project.getTenderDeadlineInDays() == 0 || project.getTenderDeadlineInDays() == -1) {
                // Set deadline to -1 to exclude it from further evaluations
                project.setTenderDeadlineInDays(-1);

                // Decide who gets the project
                if (project.getInvolvedPlayers().size() == 1) {
                    logger.debug("Project {} has no tender process. Assigning project to player {}.",
                            project.getName(), project.getInvolvedPlayers().get(0).getId());

                    // Inform winner with a confirmation message
                    assignProjectToPlayer(project.getInvolvedPlayers().get(0), project);
                }
            } else {
                // For tender processes, just decrease the time left for tender participation
                project.decreaseTimeLeftForTender();
            }
        }
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

    Player getPlayerByWebSocket(WebSocket websocket) {
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
        int riskAssessmentCost = (int) Params.PROJECT_RISK_ASSESSMENT_COST;
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
        messagingService.sendMessageToPlayer(player, GSON.toJson(riskAssessedConfirmation));
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
            // Remove employee from all projects
            for (Project project : getProjectService().getProjects()) {
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
        messagingService.sendMessageToPlayer(player, GSON.toJson(employeeDismissedEvent));

        // Send new employee to all players' TalentMarkets in the game
        GameEvent<ArrayList<Employee>> employeeEvent = new GameEvent<>(EventType.TALENTS_ADDED);
        employeeEvent.setPayload(new ArrayList<>(List.of(employee)));
        messagingService.broadcastToAllPlayers(GSON.toJson(employeeEvent));
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
        sendEmployeeUpdate(player, employee);
    }

    public void conductTeamEstimation(int projectId, Player player) {
        Project project = projectService.getProjectById(projectId);
        if (project == null) {
            logger.error("Project with ID {} not found.", projectId);
            return;
        }

        // Calculate remaining value of the project
        int remainingValue = project.getTotalValue() - project.getEarnedValue();
        // Remaining value and project volume affect estimation duration, but it's at least 2 days
        int estimationDurationInDays = (int) Math.max(2, 3 * Math.log(remainingValue) - 30);
        logger.debug("Estimation duration for remaining value {} € project {}: {} days", remainingValue, project.getName(), estimationDurationInDays);

        // Add status effect with decreased productivity for all employees in the project
        for (Employee employee : player.getEmployees()) {
            if (projectEmployeesMap.containsKey(project) && projectEmployeesMap.get(project).contains(employee)) {
                employee.addStatusEffect(new StatusEffect(
                        StatusEffectType.PRODUCTIVITY, 0.1f,
                        "Estimating project", estimationDurationInDays));
            }
        }

        // Calculate the estimation and add it to the project
        project.estimateProgress(currentTick);

        // Send project update to player
        messagingService.sendProjectUpdateToPlayer(player, project);
    }

    public void assignProjectToPlayer(Player player, Project project) {
        // Assign the project to the player
        project.addParty(player);
        project.setAcquiredAt(currentTick);

        // Add the project to the project-employee map
        projectEmployeesMap.put(project, new ArrayList<>());

        // Send PROJECT_UPDATED to all players (-> important for tenders!)
        GameEvent<Project> projectUpdatedEvent = new GameEvent<>(EventType.PROJECT_UPDATED);
        projectUpdatedEvent.setPayload(project);
        messagingService.broadcastToAllPlayers(GSON.toJson(projectUpdatedEvent));

        // Send PROJECT_RECEIVED event to the player
        GameEvent<Project> projectReceivedEvent = new GameEvent<>(EventType.PROJECT_RECEIVED);
        projectReceivedEvent.setPayload(project);
        messagingService.sendMessageToPlayer(player, GSON.toJson(projectReceivedEvent));
    }

    public void addPropertyChangeListener(PropertyChangeListener pcl) {
        support.addPropertyChangeListener(pcl);
    }
}
