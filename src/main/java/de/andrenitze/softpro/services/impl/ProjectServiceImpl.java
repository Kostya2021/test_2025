package de.andrenitze.softpro.services.impl;

import de.andrenitze.softpro.Game;
import de.andrenitze.softpro.Player;
import de.andrenitze.softpro.config.GameParameters;
import de.andrenitze.softpro.domains.accounting.AccountCategory;
import de.andrenitze.softpro.domains.accounting.AccountingEntry;
import de.andrenitze.softpro.domains.accounting.TransactionType;
import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.projects.*;
import de.andrenitze.softpro.events.GameEvent;
import de.andrenitze.softpro.events.EventType;
import de.andrenitze.softpro.domains.employees.StatusEffect;
import de.andrenitze.softpro.domains.employees.StatusEffectType;
import de.andrenitze.softpro.services.ProjectService;
import lombok.Getter;
import lombok.Setter;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import static de.andrenitze.softpro.Game.*;
import static de.andrenitze.softpro.GameServer.*;
import static de.andrenitze.softpro.domains.projects.ProjectType.COMPLIANCE_PROJECT_NAMES;
import static de.andrenitze.softpro.events.GameEventHandler.PARTY_CLIENT;
import static java.lang.Math.*;

@Service
@Primary
public class ProjectServiceImpl implements ProjectService {
    public static final double CONTRACTOR_CANCELLATION_PENALTY = 0.15;  // 15% penalty when contractor cancels (kill dead horse)
    public static final double CLIENT_CANCELLATION_PENALTY = 0.3;      // 30% penalty when client cancels (due to delay)
    public static final int BASE_PRODUCTIVITY_VALUE = 1000; // How much value one person (FTE) can produce in one day
    public static final double PROFIT_MARGIN = 0.3;
    public static final float PROJECT_SPAWN_PROBABILITY = 0.1f;
    public static final float COMPLIANCE_PROJECT_SPAWN_PROBABILITY = 0.01f;
    @Setter private Game game;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final SkillServiceImpl skillService;
    @Getter
    private final AccountingServiceImpl accountingService;
    @Getter
    private ArrayList<Project> projects = new ArrayList<>();
    static final String FAMILIARIZATION_WITH_NEW_DOMAIN = "Familiarization with new project domain";
    static final String FAMILIARIZATION_WITH_NEW_TYPE = "Familiarization with new project type";
    @Getter
    private final ProblemGenerator problemGenerator = new ProblemGenerator();
    private final ProjectEmployeeMappingImpl mappingService;
    private final MessagingServiceImpl messagingService;

    @Autowired
    public ProjectServiceImpl(MessagingServiceImpl messagingService,
                              SkillServiceImpl skillService,
                              AccountingServiceImpl accountingService,
                              @Qualifier("projectEmployeeMapping") ProjectEmployeeMappingImpl projectEmployeeMapping) {
        this.messagingService = messagingService;
        this.skillService = skillService;
        this.accountingService = accountingService;
        this.mappingService = projectEmployeeMapping;
    }

    public void conductWorkOnAllProjects(int currentTick, LocalDate currentDate, ConcurrentMap<Project,
            ArrayList<Employee>> projectEmployeesMap) {
        // No work on weekends
        if (currentDate.getDayOfWeek() == DayOfWeek.SATURDAY || currentDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return;
        }

        // For all projects that are started AND have employees assigned
        Iterator<Map.Entry<Project, ArrayList<Employee>>> iterator = mappingService.getProjectEmployeesMap().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Project, ArrayList<Employee>> entry = iterator.next();
            Project project = entry.getKey();
            ArrayList<Employee> employees = entry.getValue();

            // Only process started projects with assigned employees
            if (project.getStartedAt() != 0 && !employees.isEmpty()) {
                addEarnedValueForEachEmployee(project, employees, currentTick);

                var projectObject = new JSONObject();

                // Finish the project if completed
                if (project.isCompleted()) {
                    // Remove all status effects on employees related to this project
                    mappingService.getProjectEmployeesMap().get(project).forEach(employee -> {
                        employee.removeStatusEffectsByDescription(FAMILIARIZATION_WITH_NEW_DOMAIN);
                        employee.removeStatusEffectsByDescription(FAMILIARIZATION_WITH_NEW_TYPE);
                    });

                    // Send reward
                    handleProjectCompletion(project, employees, currentTick, projectObject);

                    // Remove the project from employees map, so that employees are unassigned
                    project.setEarnedValue(project.getTotalValue());
                    iterator.remove();

                    projectObject.put("completedAt", currentTick);
                }

                // Build a small custom event to just send new project progress and success metrics
                projectObject.put("id", project.getId());
                projectObject.put("earnedValue", project.getEarnedValue());
                projectObject.put("tick", currentTick);

                GameEvent<JSONObject> projectUpdatedEvent = new GameEvent<>(EventType.PROJECT_UPDATED);
                projectUpdatedEvent.setPayload(projectObject);

                // Send update to all involved players
                for (Player player : project.getInvolvedPlayers()) {
                    messagingService.sendEventToPlayer(player, projectUpdatedEvent);
                }
            }
        }
    }

    private void handleProjectCompletion(Project project, ArrayList<Employee> employees, int currentTick, JSONObject projectObject) {
        for (Player player : project.getInvolvedPlayers()) {
            int profit = (int) round(project.getTotalValue() * ProjectServiceImpl.PROFIT_MARGIN);

            float overduePenaltyMultiplier = 1;
            int daysLeft = project.getDeadline() - (currentTick - project.getStartedAt());
            if (daysLeft < 0) {
                // Per 1% delayed delivery, return 2% less win margin
                overduePenaltyMultiplier = 1 - ((float) abs(daysLeft) / project.getDeadline() * 2);
                float penalty = profit * (1 - overduePenaltyMultiplier);
                if (game.getLevel() == 1) {
                    penalty = 0;
                }
                projectObject.put("penalty", penalty);

                project.setPenalty(penalty);
                logger.debug("Project finished, but was overdue. Reducing profit by {} as penalty.", penalty);
            }

            // Prevent losses in level 1
            if (game.getLevel() != 1) {
                profit = (int) (profit * overduePenaltyMultiplier);
            }

            projectObject.put("profit", profit);
            project.setProfit(profit);
            // Don't win or lose anything in level 1 or if it's a compliance project
            if (game.getLevel() == 1 || project.getType() == ProjectType.COMPLIANCE) {
                profit = 0;
            }

            player.addFunds(profit);
            AccountingEntry projectProfitEntry = new AccountingEntry(player, currentTick, profit,
                    AccountCategory.CREDIT_PROJECTS, TransactionType.CREDIT, "Project completed");
            accountingService.addEntry(projectProfitEntry);
            game.getMessagingService().sendFundsUpdateToPlayer(player);

            // Calculate player's XP gained in this project
            float xp = calculateXP(project);
            player.addXp((int) xp);

            GameEvent<Player> playerUpdateEvent = new GameEvent<>();
            playerUpdateEvent.setType(EventType.PLAYER_UPDATED);
            playerUpdateEvent.setPayload(player);
            game.getMessagingService().sendMessageToPlayer(player, gson.toJson(playerUpdateEvent));

            // After project completion, send gained XP of employees to player
            for (Employee employee : employees) {
                game.getMessagingService().sendEmployeeUpdate(player, employee);
            }
        }

        // Calculate project result quality (0-100)
        calculateProjectQuality(project, employees, projectObject);
    }

    private void calculateProjectQuality(Project project, ArrayList<Employee> employees, JSONObject projectObject) {
        HashMap<Employee, Integer> projectExperience = new HashMap<>();
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
        projectExperience.forEach((employee, experience) -> {
            var contribution = (float) experience / (float) finalTotalDaysWorkedOnProject;
            weightedProjectContributions.put(employee, contribution);
            logger.debug("Project work done by {}: {}%", employee.getName(), weightedProjectContributions.get(employee)*100);
        });

        // Quality is the average of each employees' individual skill for this project weighted by the amount of work (= contribution)
        AtomicReference<Float> totalProjectQuality = new AtomicReference<>((float) 0);
        skillMultipliers.forEach((employee, skill) ->
                totalProjectQuality.updateAndGet(quality ->
                        quality + skill * weightedProjectContributions.get(employee)));

        int projectQuality = (int) (totalProjectQuality.get() * 100);
        logger.debug("Project overall quality: {}/100", projectQuality);

        project.setQuality(projectQuality);
        projectObject.put("quality", projectQuality);
    }

    private void addEarnedValueForEachEmployee(Project project, ArrayList<Employee> employees, int currentTick) {
        if (project == null || employees == null) {
            logger.error("Project or employees list is null.");
            return;
        }

        float onboardingFactor = calculateTeamOnboardingFactor(project, employees, currentTick);

        // Special case: Compliance projects have simplified productivity calculation
        if (project.getType() == ProjectType.COMPLIANCE) {
            int earnedValue = (int)(BASE_PRODUCTIVITY_VALUE * 0.8);
            project.addEarnedValue(earnedValue, currentTick);
            return;
        }

        // Process each employee's contribution
        for (Employee employee : employees) {
            if (employee.isSick()) {
                continue;
            }

            int earnedValue = calculateEmployeeEarnedValue(employee, project, currentTick, onboardingFactor);
            updateProjectWithEarnedValue(project, earnedValue, currentTick);

            // Increase the employee's experience
            employee.gainExperience(project, 1);
        }
    }

    private float calculateTeamOnboardingFactor(Project project, ArrayList<Employee> employees, int currentTick) {
        // Rule #3: Adding people to a late software project makes it later (Brooks' law)
        if (!project.isRampingUp(currentTick) && project.hasOnboardingEmployees(employees)) {
            float factor = calculateOnboardingFactor(project, employees);
            logger.debug("Averaged onboarding factor (decreased productivity) for the whole team: {}", factor);
            return factor;
        }
        return 1.0f; // No onboarding required (safe period or no new employees)
    }

    public int calculateEmployeeEarnedValue(Employee employee, Project project, int currentTick, float onboardingFactor) {
        // Base productivity
        int earnedValue = BASE_PRODUCTIVITY_VALUE;

        // Apply experience factors
        earnedValue = applyExperienceFactors(employee, project, earnedValue);

        // Apply context switching penalty
        earnedValue = applyContextSwitchingPenalty(employee, earnedValue);

        // Apply productivity ramp-up for new employees on the project
        earnedValue = applyRampUpFactor(employee, project, earnedValue);

        // Apply team onboarding factor
        earnedValue = (int)(earnedValue * onboardingFactor);

        // Apply organizational skills factor
        earnedValue = (int)(earnedValue * calculateSkillsFactor(project));

        // Apply employee status effects
        earnedValue = applyStatusEffects(employee, earnedValue);

        // Apply unsolved problems penalty
        earnedValue = applyUnsolvedProblemsPenalty(project, earnedValue, currentTick);

        return earnedValue;
    }

    private float calculateSkillsFactor(Project project) {
        List<Player> players = project.getInvolvedPlayers();

        // If only one player is working on the project
        if (players.size() == 1) {
            Player player = players.getFirst();
            if (skillService.playerHasSkill(player, "pmo")) {
                return 1.05f;
            }
        }
        return 1.0f;
    }

    private int applyExperienceFactors(Employee employee, Project project, int baseValue) {
        int typeXP = employee.getExperienceInDaysByProjectType(project.getType());
        int domainXP = employee.getExperienceInDaysByProjectDomain(project.getDomain());

        float productivityFactor = calculateProductivityFactor(typeXP, domainXP);
        return (int)(baseValue * productivityFactor * 2);
    }

    private int applyContextSwitchingPenalty(Employee employee, int earnedValue) {
        // Rule #1: Context changes decrease employee productivity
        int numberOfParallelProjects = getNumberOfParallelProjectsForEmployee(employee);

        return switch (numberOfParallelProjects) {
            case 0, 1 -> earnedValue;
            case 2 -> (int)(earnedValue * 0.4);
            case 3 -> (int)(earnedValue * 0.2);
            case 4 -> (int)(earnedValue * 0.1);
            case 5 -> (int)(earnedValue * 0.05);
            default -> 1;
        };
    }

    private int applyRampUpFactor(Employee employee, Project project, int earnedValue) {
        // Rule #2: Productivity ramp-up for new staff
        float experience = employee.getExperienceInDaysByProject(project);
        if (experience < 30) {
            float rampUpProductivityFactor = (float)(1.022595 - 1.02502 * exp(-0.1399307 * experience));
            return (int)(earnedValue * rampUpProductivityFactor);
        }
        return earnedValue;
    }

    private int applyStatusEffects(Employee employee, int earnedValue) {
        // Rule #6: Apply status effects
        if (employee.getStatusEffects().isEmpty()) {
            return earnedValue;
        }

        float multiplier = 1.0f;
        for (StatusEffect effect : employee.getStatusEffects()) {
            if (effect.getType() == StatusEffectType.PRODUCTIVITY) {
                multiplier *= effect.getMultiplier();
            }
        }

        return (int)(earnedValue * multiplier);
    }

    private int applyUnsolvedProblemsPenalty(Project project, int earnedValue, int currentTick) {
        // Rule #7: Penalty for unsolved problems
        if (project.getUnsolvedProblems().isEmpty()) {
            return earnedValue;
        }

        int gracePeriod = 30;
        int unsolvedLingeringProblems = (int)project.getUnsolvedProblems().stream()
                .filter(problem -> currentTick - problem.getOccurredAt() > gracePeriod)
                .count();

        return (int)(earnedValue * pow(0.75, unsolvedLingeringProblems));
    }

    private void updateProjectWithEarnedValue(Project project, int earnedValue, int currentTick) {
        // Set project start time if this is the first earned value
        if (project.getEarnedValue() == 0 && earnedValue > 0) {
            project.setStartedAt(currentTick);
        }

        // Add the earned value to the project
        project.addEarnedValue(earnedValue, currentTick);
    }

    private float calculateProductivityFactor(int typeXP, int domainXP) {
        // Weights for project type and domain experience
        float typeXpWeight = 0.25f;
        float domainXpWeight = 0.75f;

        // Base productivity value (if experience = 0)
        float baseProductivity = 0.25f;

        return (float) (baseProductivity +
                (1 - baseProductivity) * (
                        typeXpWeight * (1 - exp(-0.001 * typeXP)) +
                                domainXpWeight * (1 - exp(-0.001 * domainXP))
                ));
    }

    private float calculateOnboardingFactor(Project project, ArrayList<Employee> employees) {
        ArrayList<Float> onboardingFactors = new ArrayList<>();

        for (Employee employee : employees) {
            // FIXED: 30,4 (10% of a 304-day project)
            float onboardingDays = GameParameters.EMPLOYEE_ONBOARDING_TIME_IN_PERCENT * project.getScheduledDuration();

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
            float onboardingFactor = 1 - (1 - onboardingProgress) * GameParameters.MAXIMUM_ONBOARDING_PRODUCTIVITY_DECREASE;
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

        for (Map.Entry<Project, ArrayList<Employee>> entry : mappingService.getProjectEmployeesMap().entrySet()) {
            ArrayList<Employee> employees = entry.getValue();
            Project project = entry.getKey();

            if (employees.contains(employee) && project.hasBeenStarted()) {
                numberOfProjects++;
            }
        }
        return numberOfProjects;
    }

    public void cancelProject(Player player, Project project, String cancelledBy) {
        if (project == null) {
            logger.error("Project not found while attempting to cancel.");
            return;
        }

        if (project.isCompleted()) {
            logger.error("Project {} is already completed.", project.getName());
            return;
        }

        if (project.getCancelledAt() != 0) {
            logger.error("Project {} is already cancelled.", project.getName());
            return;
        }

        if (project.getInvolvedPlayers().size() > 1) {
            logger.error("Project {} has more than one player involved. Only the project owner can cancel it.", project.getName());
            return;
        }

        if (!project.getInvolvedPlayers().contains(player)) {
            logger.error("Player {} is not involved in project {}.", player.getId(), project.getName());
            return;
        }

        if (project.getType() == ProjectType.COMPLIANCE) {
            logger.error("Compliance projects cannot be cancelled.");
            return;
        }

        project.setCancelledAt(game.getCurrentTick());
        project.setCancelledBy(cancelledBy);

        // Apply different penalty rates based on who cancelled
        double penaltyRate = PARTY_CLIENT.equals(cancelledBy)
                ? CLIENT_CANCELLATION_PENALTY
                : CONTRACTOR_CANCELLATION_PENALTY;

        int cancellationPenalty = 0;
        if (game.getLevel() != 1) {
            cancellationPenalty = (int) (project.getTotalValue() * penaltyRate);
        }
        project.setPenalty(cancellationPenalty);

        // If the project has started, apply the penalty
        if (project.getStartedAt() > 0) {
            // Update funds
            player.subtractFunds(cancellationPenalty);
            game.getMessagingService().sendFundsUpdateToPlayer(player);

            // Add accounting entry
            accountingService.addEntry(new AccountingEntry(
                    player,
                    game.getCurrentTick(),
                    cancellationPenalty,
                    AccountCategory.PENALTIES,
                    TransactionType.DEBIT,
                    "Cancellation penalty for project: " + project.getName()
            ));
        }

        // Remove any employees assigned to this project
        if (mappingService.getProjectEmployeesMap().containsKey(project)) {
            mappingService.getProjectEmployeesMap().get(project).clear();
        }

        // Remove player from project
        project.removeParty(player);

        // Create a project update event to notify the player (cancelledAt, penalty and more...)
        GameEvent<Project> projectCancelledEvent = new GameEvent<>();
        projectCancelledEvent.setType(EventType.PROJECT_UPDATED);
        projectCancelledEvent.setPayload(project);

        // Notify the player
        game.getMessagingService().sendMessageToPlayer(player, gson.toJson(projectCancelledEvent));

        // If the project was acquired but not started completely remove it from the game.
        if (project.getAcquiredAt() > 0 && project.getStartedAt() == 0) {
            getProjects().remove(project);
            mappingService.getProjectEmployeesMap().remove(project);
        }

        logger.debug("Project {} cancelled by player {}", project.getName(), player.getId());
    }

    public void cancelOverdueProjects() {
        List<Project> projectsToCancel = new ArrayList<>();

        // Identify projects that meet the cancellation criteria
        for (Project project : getProjects()) {
            // Check if project should be automatically cancelled
            if (shouldAutomaticallyCancel(project, game.getCurrentTick())) {
                projectsToCancel.add(project);
            }
        }

        // Process cancellations outside the iteration loop
        for (Project project : projectsToCancel) {
            logger.info("Auto-cancelling project {} due to excessive schedule overrun", project.getName());

            // Cancel for each involved player
            for (Player player : project.getInvolvedPlayers()) {
                cancelProject(player, project, PARTY_CLIENT);
            }
        }
    }

    /**
     * Checks if the project should be automatically cancelled based on criteria:
     * - Project is over 100% schedule overrun
     * - Less than 50% complete
     * - Not a compliance project
     *
     * @param project The project to check
     * @param currentTick Current game tick
     * @return true if project should be cancelled, false otherwise
     */
    private boolean shouldAutomaticallyCancel(Project project, int currentTick) {
        // Don't cancel if not started, already completed or already cancelled
        if (!project.hasBeenStarted() || project.isCompleted() || project.getCancelledAt() > 0) {
            return false;
        }

        // Don't cancel compliance projects
        if (project.getType() == ProjectType.COMPLIANCE) {
            return false;
        }

        // Don't cancel projects with no deadline (deadline <= 0)
        if (project.getDeadline() <= 0) {
            return false;
        }

        // Calculate progress percentage
        int progressPercentage = (int)(100.0 * project.getEarnedValue() / project.getTotalValue());

        // Calculate schedule overrun
        int scheduledEndDate = project.getStartedAt() + project.getDeadline();
        int overrunDays = currentTick - scheduledEndDate;
        int scheduleOverrunPercentage = (int)(100.0 * overrunDays / project.getDeadline());

        // Cancel if overrun > 100% and progress < 50%
        return scheduleOverrunPercentage > 100 && progressPercentage < 50;
    }

    public void setProjects(List<Object> objects) {
        for (Object object : objects) {
            if (object instanceof Project project) {
                projects.add(project);
            }
        }
    }

    public void addProject(Project project) {
        // Check if project id already exists, if not, add the project. Also, initialize the project employees map.
        if (getProjectById(project.getId()) == null && projects.add(project)) {
            mappingService.getProjectEmployeesMap().put(project, new ArrayList<>());
        }
    }

    public Project getProjectById(int projectId) {
        for (Project project : projects) {
            if (project.getId() == projectId) {
                return project;
            }
        }
        return null;
    }

    public void startProject(Project project, int startedAt) {
        if (project == null) {
            logger.error("Project not found.");
            return;
        }

        project.setStartedAt(startedAt);
    }

    public void randomlySpawnComplianceProjects() {
        // Don't auto-spawn compliance projects in level 1
        if (game.getLevel() == 1) {
            return;
        }

        Player player = game.getPlayerService().getPlayers().values().iterator().next();

        // Only have one compliance project at a time
        if (getProjects().stream().noneMatch(project -> project.getType() == ProjectType.COMPLIANCE) &&
                RANDOM.nextFloat() <= COMPLIANCE_PROJECT_SPAWN_PROBABILITY) {
            // Generate a new compliance project
            Project project = new Project(ProjectType.COMPLIANCE, "Compliance", RiskLevel.LOW, false);

            project.setPublishedAt(game.getCurrentTick());
            project.setAcquiredAt(game.getCurrentTick()); // Immediately acquired: Frontend will show it as "acquired"
            project.addParty(player); // Add the player as involved party (also important for frontend)
            project.setDeadline(0);
            // Select a name from a list of predefined names
            project.setName(COMPLIANCE_PROJECT_NAMES.get(RANDOM.nextInt(COMPLIANCE_PROJECT_NAMES.size())));
            getProjects().add(project);

            // Add to project-employee map
            mappingService.addProject(project, new ArrayList<>());

            // Immediately assign the project to all players
            GameEvent<Project> newProjectEvent = new GameEvent<>(EventType.PROJECT_RECEIVED);
            newProjectEvent.setPayload(project);
            logger.debug("New compliance project spawned for all players: {}", project.getName());
            game.getMessagingService().broadcastToAllPlayers(getGson().toJson(newProjectEvent));
        }
    }

    private void notifyInvolvedPlayers(Project project) {
        GameEvent<HashMap<String, Integer>> projectStartedEvent = new GameEvent<>(EventType.PROJECT_STARTED);
        HashMap<String, Integer> payload = new HashMap<>();
        payload.put("projectId", project.getId());
        payload.put("startedAt", project.getStartedAt());
        projectStartedEvent.setPayload(payload);

        // Notify involved players about forced start
        project.getInvolvedPlayers().forEach(player ->
                game.getMessagingService().sendMessageToPlayer(player, getGson().toJson(projectStartedEvent)));
    }

    public void conductTeamEstimation(int projectId, Player player) {
        Project project = getProjectById(projectId);
        if (project == null) {
            logger.error("Project with ID {} not found.", projectId);
            return;
        }

        // Calculate remaining value of the project
        int remainingValue = project.getTotalValue() - project.getEarnedValue();
        // Remaining value and project volume affect estimation duration, but it's at least 2 days
        int estimationDurationInDays = (int) max(2, 3 * log(remainingValue) - 30);
        logger.debug("Estimation duration for remaining value {} € project {}: {} days", remainingValue, project.getName(), estimationDurationInDays);

        // Add status effect with decreased productivity for all employees in the project
        for (Employee employee : player.getEmployees()) {
            if (mappingService.getProjectEmployeesMap().containsKey(project) && mappingService.getProjectEmployeesMap().get(project).contains(employee)) {
                employee.addStatusEffect(new StatusEffect(
                        StatusEffectType.PRODUCTIVITY, 0.1f,
                        "Estimating project", estimationDurationInDays));
            }
        }

        // Calculate the estimation and add it to the project
        project.estimateProgress(game.getCurrentTick());

        // Send project update to player
        game.getMessagingService().sendProjectUpdateToPlayer(player, project);
    }

    public void assignProjectToPlayer(Player player, Project project) {
        // Assign the project to the player
        project.addParty(player);
        project.setAcquiredAt(game.getCurrentTick());

        // Add the project to the project-employee map
        addProject(project);

        // Send PROJECT_UPDATED to all players (-> important for tenders!)
        GameEvent<Project> projectUpdatedEvent = new GameEvent<>(EventType.PROJECT_UPDATED);
        projectUpdatedEvent.setPayload(project);
        game.getMessagingService().broadcastToAllPlayers(getGson().toJson(projectUpdatedEvent));

        // Send PROJECT_RECEIVED event to the player
        GameEvent<Project> projectReceivedEvent = new GameEvent<>(EventType.PROJECT_RECEIVED);
        projectReceivedEvent.setPayload(project);
        game.getMessagingService().sendMessageToPlayer(player, getGson().toJson(projectReceivedEvent));
    }

    public void evaluateTenderProcesses() {
        for (Project project : getProjects()) {
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
                            project.getName(), project.getInvolvedPlayers().getFirst().getId());

                    // Inform winner with a confirmation message
                    assignProjectToPlayer(project.getInvolvedPlayers().getFirst(), project);
                }
            } else {
                // For tender processes, just decrease the time left for tender participation
                project.decreaseTimeLeftForTender();
            }
        }
    }

    public void removeStaleTenders() {
        // Dont remove tenders in level 1
        if (game.getLevel() == 1) {
            return;
        }

        // Remove tenders that have been on the market for a long time and store them in a separate array
        List<Project> staleTenders = new ArrayList<>();
        for (Iterator<Project> iterator = getProjects().iterator(); iterator.hasNext();) {
            Project project = iterator.next();
            if (project.getEarnedValue() == 0 &&
                    project.getInvolvedPlayers().isEmpty() &&
                    project.getPublishedAt() + STALE_TENDERS_KILL_DAYS < game.getCurrentTick()) {
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
        game.getMessagingService().broadcastToAllPlayers(getGson().toJson(tendersRemovedEvent));
    }

    public void createProblemsInProjects() {
        // In all running projectService.getProjects()...
        for (Project project : getProjects()) {
            // If it's not running or completed, skip to the next project
            if (project.getStartedAt() != 0 && !project.isCompleted()) {
                // For now, with a fixed chance for a problem to occur,
                // (can be adjusted later depending on project volume, risk level, etc.)
                double problemSpawnProbability = 0.01;
                int maxUnsolvedProblemsPerProject = game.getLevel(); // In higher levels, more problems can occur

                // but not more than a certain number problems per project
                if (RANDOM.nextFloat() <= problemSpawnProbability
                        && project.getUnsolvedProblems().size() < maxUnsolvedProblemsPerProject) {
                    // Take all problems of the project
                    List<Problem> occurredProblems = project.getProblems();

                    // Create a problem that has not occurred in the project before (independent of resolution state)
                    Problem problem = problemGenerator.generateRandomNewProblem(occurredProblems);
                    if (problem != null) { // Only add if a new problem is generated
                        // Add the problem to the project
                        problem.setOccurredAt(game.getCurrentTick());
                        project.addProblem(problem);

                        // Inform all involved players about the new problem
                        logger.debug("New problem in project {} ({}): {}", project.getId(), project.getName(), problem.getTranslationKey());

                        project.getInvolvedPlayers().forEach(player -> {
                            GameEvent<Project> projectUpdatedEvent = new GameEvent<>(EventType.PROJECT_UPDATED);
                            projectUpdatedEvent.setPayload(project);
                            game.getMessagingService().sendMessageToPlayer(player, getGson().toJson(projectUpdatedEvent));
                        });
                    }
                }
            }
        }
    }

    public void startStaleProjects() {
        // Don't do that in level 1
        if (game.getLevel() == 1) {
            return;
        }

        // Don't do it for COMPLIANCE projects, independent of startedAt, acquiredAt etc.
        for (Project project : getProjects()) {
            if (project.getType() == ProjectType.COMPLIANCE) {
                continue;
            }

            // For all projects that have been acquired, but not started after MAX(30 days, 10% of project duration)
            if (project.getAcquiredAt() != 0 && project.getStartedAt() == 0) {
                int daysPassed = game.getCurrentTick() - project.getAcquiredAt();
                if (daysPassed >= max(30, project.getScheduledDuration() / 10)) {
                    // Start the project and inform involved players
                    startProject(project, game.getCurrentTick() - 1);
                    notifyInvolvedPlayers(project);
                    logger.debug("Project {} force started after {} days.", project.getName(), daysPassed);
                }
            }
        }
    }

    public void randomlySpawnProjectTenders() {
        // There is only one player in level 1
        Player p = game.getPlayerService().getPlayers().values().iterator().next();

        // Don't spawn new projects in level 1 before the first mission is completed
        if (game.getLevel() == 1 && p.getMissions().getFirst().isNotCompleted()) {
            return;
        }

        if (RANDOM.nextFloat() <= PROJECT_SPAWN_PROBABILITY) {
            // Generate a new project
            Project project;

            // For level 1, make sure that it's only easy and small projects
            if (game.getLevel() == 1) {
                // 25% chance for a perfect project
                if (RANDOM.nextFloat() <= 0.75) {
                    // Low-risk, small projects
                    project = new Project(RiskLevel.LOW).initialize();

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

                    project = new Project(type, domain, RiskLevel.LOW, false);
                }
            } else {
                project = new Project().initialize();
            }

            project.setPublishedAt(game.getCurrentTick());
            addProject(project);

            // Initialize project-employee map
            mappingService.getProjectEmployeesMap().put(project, new ArrayList<>(2));

            // Inform players about the new tender
            GameEvent<Project> newTenderEvent = new GameEvent<>(EventType.NEW_TENDER);
            newTenderEvent.setPayload(project);

            game.getMessagingService().broadcastToAllPlayers(getGson().toJson(newTenderEvent));
        }
    }

    public void loadProblems() {
        problemGenerator.loadProblemsByLevel(game.getLevel());
    }

    public void assessProjectRiskForPlayer(int projectId, Player player) {
        Project project = getProjectById(projectId);
        if (project == null) {
            logger.error("Project with ID {} could not be found.", projectId);
            return;
        }

        // Deduct funds from player
        int riskAssessmentCost = (int) GameParameters.PROJECT_RISK_ASSESSMENT_COST;
        game.getAccountingService().addEntry(new AccountingEntry(
                player,
                game.getCurrentTick(),
                riskAssessmentCost,
                AccountCategory.DEBIT_PROJECTS,
                TransactionType.DEBIT,
                "Project risk assessment")
        );
        player.subtractFunds(riskAssessmentCost);
        game.getMessagingService().sendFundsUpdateToPlayer(player);

        GameEvent<Project> riskAssessedConfirmation = new GameEvent<>(EventType.RISK_ASSESSMENT_CONFIRMED);
        riskAssessedConfirmation.setPayload(project);
        game.getMessagingService().sendEventToPlayer(player, riskAssessedConfirmation);
    }
}