package de.andrenitze.softpro.services.impl;

import de.andrenitze.softpro.Player;
import de.andrenitze.softpro.config.GameParameters;
import de.andrenitze.softpro.domains.accounting.AccountCategory;
import de.andrenitze.softpro.domains.accounting.AccountingEntry;
import de.andrenitze.softpro.domains.accounting.TransactionType;
import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.employees.StatusEffect;
import de.andrenitze.softpro.domains.employees.StatusEffectType;
import de.andrenitze.softpro.domains.projects.*;
import de.andrenitze.softpro.services.ProjectEmployeeMappingService;
import de.andrenitze.softpro.services.ProjectService;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static de.andrenitze.softpro.Game.calculateXP;
import static de.andrenitze.softpro.GameServer.RANDOM;
import static de.andrenitze.softpro.GameServer.gson;
import static de.andrenitze.softpro.domains.projects.ProjectType.COMPLIANCE_PROJECT_NAMES;
import static de.andrenitze.softpro.events.GameEventHandler.PARTY_CLIENT;
import static java.lang.Math.*;

@Service
@Primary
public class ProjectServiceImpl implements ProjectService {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    public static final int STALE_TENDERS_KILL_DAYS = 548;
    public static final double CONTRACTOR_CANCELLATION_PENALTY = 0.15;  // 15% penalty when contractor cancels (kill dead horse)
    public static final double CLIENT_CANCELLATION_PENALTY = 0.3;      // 30% penalty when client cancels (due to delay)
    public static final int BASE_PRODUCTIVITY_VALUE = 1000; // How much value one person (FTE) can produce in one day
    public static final double PROFIT_MARGIN = 0.3;
    public static final float PROJECT_SPAWN_PROBABILITY = 0.1f;
    public static final float COMPLIANCE_PROJECT_SPAWN_PROBABILITY = 0.01f;
    static final String FAMILIARIZATION_WITH_NEW_DOMAIN = "Familiarization with new project domain";
    static final String FAMILIARIZATION_WITH_NEW_TYPE = "Familiarization with new project type";
    @Getter @Setter private List<Project> projects = new ArrayList<>();
    @Getter private final ProblemGenerator problemGenerator = new ProblemGenerator();

    @Getter private final AccountingServiceImpl accountingService;
    private final SkillServiceImpl skillService;
    private final ProjectEmployeeMappingService projectEmployeeService;
    private final Map<Integer, Project> previousProjectStates = new HashMap<>();

    @Autowired
    public ProjectServiceImpl(AccountingServiceImpl accountingService, SkillServiceImpl skillService,
                              ProjectEmployeeMappingService projectEmployeeService) {
        this.accountingService = accountingService;
        this.skillService = skillService;
        this.projectEmployeeService = projectEmployeeService;

        // Initialize the projects list
        setProjects(new ArrayList<>());
    }

    public List<Project> conductWorkOnAllProjects(int tick, int level, LocalDate currentDate) {
        List<Project> projectsWithChanges = new ArrayList<>();

        // No work on weekends
        if (currentDate.getDayOfWeek() == DayOfWeek.SATURDAY || currentDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return projectsWithChanges;
        }

        // For all projects...
        Iterator<Map.Entry<Project, ArrayList<Employee>>> iterator = projectEmployeeService.getProjectEmployeesMap().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Project, ArrayList<Employee>> entry = iterator.next();
            Project project = entry.getKey();
            ArrayList<Employee> employees = entry.getValue();

            // ...which have started and have employees assigned:
            if (project.getStartedAt() != 0 && !employees.isEmpty()) {
                addEarnedValueForEachEmployee(project, employees, tick);

                if (project.isCompleted()) {
                    handleProjectCompletion(project, employees, tick, level);

                    // Remove the project from the employees map to avoid memory leaks
                    iterator.remove();
                }

                // We assume that under the conditions above at least the earned value changed.
                // So add the project to the list of projects with changes
                projectsWithChanges.add(project);
            }
        }
        return projectsWithChanges;
    }

    private void handleProjectCompletion(Project project, ArrayList<Employee> employees, int currentTick, int level) {
        for (Player player : project.getInvolvedPlayers()) {
            // Remove all status effects on employees related to this project
            projectEmployeeService.getProjectEmployeesMap().get(project).forEach(
                    employee -> employee.removeStatusEffectsByTrigger(project)
            );

            // Make sure that earned value equals the total value
            project.setEarnedValue(project.getTotalValue());

            // Calculate profit
            int profit = (int) round(project.getTotalValue() * ProjectServiceImpl.PROFIT_MARGIN);

            float overduePenaltyMultiplier = 1;
            int daysLeft = project.getDeadline() - (currentTick - project.getStartedAt());
            if (daysLeft < 0) {
                // Per 1% delayed delivery, return 2% less win margin
                overduePenaltyMultiplier = 1 - ((float) abs(daysLeft) / project.getDeadline() * 2);
                float penalty = profit * (1 - overduePenaltyMultiplier);
                if (level == 1) {
                    penalty = 0;
                }

                project.setPenalty(penalty);
                logger.debug("Project finished, but was overdue. Reducing profit by {} as penalty.", penalty);
            }

            // Prevent losses in level 1
            if (level != 1) {
                profit = (int) (profit * overduePenaltyMultiplier);
            }

            project.setProfit(profit);
            // Don't win or lose anything in level 1 or if it's a compliance project
            if (level == 1 || project.getType() == ProjectType.COMPLIANCE) {
                profit = 0;
            }

            player.addFunds(profit);
            AccountingEntry projectProfitEntry = new AccountingEntry(player, currentTick, level, profit,
                    AccountCategory.CREDIT_PROJECTS, TransactionType.CREDIT, "Project completed");
            accountingService.addEntry(projectProfitEntry);

            // Calculate player's XP gained in this project
            float xp = calculateXP(project);
            player.addXp((int) xp);
        }

        // Calculate project result quality (0-100)
        calculateProjectQuality(project, employees);
    }

    private void calculateProjectQuality(Project project, ArrayList<Employee> employees) {
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
            projectExperience.put(employee, employee.getExperienceByProject(project));
            totalDaysWorkedOnProject += employee.getExperienceByProject(project);
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
            logger.debug("Averaged onboarding-induced productivity factor for the whole team: {}", factor);
            return factor;
        }
        return 1.0f; // No onboarding required (safe period or no new employees)
    }

    public int calculateEmployeeEarnedValue(Employee employee, Project project, int tick, float onboardingFactor) {
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
        earnedValue = applyUnsolvedProblemsPenalty(project, earnedValue, tick);

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
        float experience = employee.getExperienceByProject(project);
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
            // Use deadline to calculate onboarding time.
            int scheduledDuration = project.getScheduledDuration();
            if (scheduledDuration <= 0) {
                scheduledDuration = 90; // Default to 90 days if no deadline is set
            }
            int onboardingDays = (int) (GameParameters.EMPLOYEE_ONBOARDING_TIME_IN_PERCENT * scheduledDuration);

            // Calculate onboarding progress based on employee's experience
            float onboardingProgress = (float) employee.getExperienceByProject(project) / onboardingDays;
            if (onboardingProgress >= 1) continue; // Onboarding completed. Ignore this employee for calculation.

            // Calculate productivity decrease factor
            float onboardingFactor = 1 - (1 - onboardingProgress) * GameParameters.MAXIMUM_ONBOARDING_PRODUCTIVITY_DECREASE;
            onboardingFactors.add(onboardingFactor);

            logger.debug("{} is being onboarded in project {}: {} productivity factor, {}/{} days",
                    employee.getName(), project.getName(), onboardingFactor,
                    employee.getExperienceByProject(project), onboardingDays);
        }

        // If no employees are onboarding, return 1.0f (no productivity decrease)
        if (onboardingFactors.isEmpty()) {
            return 1.0f;
        }

        // Calculate the average onboarding factor
        return onboardingFactors.stream().reduce(0f, Float::sum) / onboardingFactors.size();
    }

    private int getNumberOfParallelProjectsForEmployee(Employee employee) {
        int numberOfProjects = 0;

        for (Map.Entry<Project, ArrayList<Employee>> entry : projectEmployeeService.getProjectEmployeesMap().entrySet()) {
            ArrayList<Employee> employees = entry.getValue();
            Project project = entry.getKey();

            if (employees.contains(employee) && project.hasBeenStarted()) {
                numberOfProjects++;
            }
        }
        return numberOfProjects;
    }

    public void cancelProject(Player player, Project project, String cancelledBy, int tick, int level) {
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

        project.setCancelledAt(tick);
        project.setCancelledBy(cancelledBy);

        // Apply different penalty rates based on who cancelled
        double penaltyRate = PARTY_CLIENT.equals(cancelledBy)
                ? CLIENT_CANCELLATION_PENALTY
                : CONTRACTOR_CANCELLATION_PENALTY;

        int cancellationPenalty = 0;
        if (level != 1) {
            cancellationPenalty = (int) (project.getTotalValue() * penaltyRate);
        }
        project.setPenalty(cancellationPenalty);

        // If the project has started, apply the penalty
        if (project.getStartedAt() > 0) {
            // Update funds
            player.subtractFunds(cancellationPenalty);

            // Add accounting entry
            accountingService.addEntry(new AccountingEntry(
                    player,
                    tick,
                    level,
                    cancellationPenalty,
                    AccountCategory.PENALTIES,
                    TransactionType.DEBIT,
                    "Cancellation penalty for project: " + project.getName()
            ));
        }

        // Remove any employees assigned to this project
        if (projectEmployeeService.getProjectEmployeesMap().containsKey(project)) {
            projectEmployeeService.getProjectEmployeesMap().get(project).clear();
        }

        // Remove player from project
        project.removeParty(player);

        // If the project was acquired but not started completely remove it from the game.
        if (project.getAcquiredAt() > 0 && project.getStartedAt() == 0) {
            getProjects().remove(project);
            projectEmployeeService.getProjectEmployeesMap().remove(project);
        }

        logger.debug("Project {} cancelled by player {}", project.getName(), player.getId());
    }

    public void cancelOverdueProjects(int tick, int level) {
        List<Project> projectsToCancel = new ArrayList<>();

        // Identify projects that meet the cancellation criteria
        for (Project project : getProjects()) {
            // Check if project should be automatically cancelled
            if (shouldAutomaticallyCancel(project, tick)) {
                projectsToCancel.add(project);
            }
        }

        // Process cancellations outside the iteration loop
        for (Project project : projectsToCancel) {
            logger.info("Auto-cancelling project {} due to excessive schedule overrun", project.getName());

            // Cancel for each involved player
            for (Player player : project.getInvolvedPlayers()) {
                cancelProject(player, project, PARTY_CLIENT, tick, level);
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

    public void addProject(Project project) {
        // Check if project id already exists, if not, add the project. Also, initialize the project employees map.
        if (getProjectById(project.getId()) == null) {
            projects.add(project);
            projectEmployeeService.getProjectEmployeesMap().put(project, new ArrayList<>());
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

    @Override
    public void initialize() {
        setProjects(new ArrayList<>());

        for (int i = 0; i < 100; i++) {
            Project project = new Project().initialize();

            // Set randomly negative publish dates to have some history of tenders
            project.setPublishedAt(round(RANDOM.nextFloat() * STALE_TENDERS_KILL_DAYS * -1));

            // Initialize the project-employee map with empty employees list
            projectEmployeeService.addProject(project, new ArrayList<>());
        }
    }

    /**
     * Generate random compliance projects for all players.
     * Compliance projects are immediately assigned to players.
     *
     * @param tick  Current game tick
     */
    public void generateRandomComplianceProjects(int tick) {
        // Only have one compliance project at a time
        if (getProjects().stream().noneMatch(project -> project.getType() == ProjectType.COMPLIANCE) &&
                RANDOM.nextFloat() <= COMPLIANCE_PROJECT_SPAWN_PROBABILITY) {
            // Generate a new compliance project
            Project project = new Project(ProjectType.COMPLIANCE, "Compliance", RiskLevel.LOW, false);

            project.setPublishedAt(tick);
            project.setAcquiredAt(tick); // Immediately acquired: Frontend will show it as "acquired"
            project.setDeadline(0);
            // Select a name from a list of predefined names
            project.setName(COMPLIANCE_PROJECT_NAMES.get(RANDOM.nextInt(COMPLIANCE_PROJECT_NAMES.size())));
            getProjects().add(project);

            // Add to project-employee map
            projectEmployeeService.addProject(project, new ArrayList<>());
        }
    }

    public void conductTeamEstimation(int projectId, Player player, int tick) {
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
            if (projectEmployeeService.getProjectEmployeesMap().containsKey(project) && projectEmployeeService.getProjectEmployeesMap().get(project).contains(employee)) {
                employee.addStatusEffect(new StatusEffect(
                        StatusEffectType.PRODUCTIVITY, 0.1f,
                        "Estimating project", estimationDurationInDays));
            }
        }

        // Calculate the estimation and add it to the project
        project.estimateProgress(tick);
    }

    public void assignProjectToPlayer(Player player, Project project, int tick) {
        // Assign the project to the player
        project.addParty(player);
        project.setAcquiredAt(tick);

        // Add the project to the project-employee map
        addProject(project);
    }

    public void evaluateTenderProcesses(int tick) {
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
                    assignProjectToPlayer(project.getInvolvedPlayers().getFirst(), project, tick);
                }
            } else {
                // For tender processes, just decrease the time left for tender participation
                project.decreaseTimeLeftForTender();
            }
        }
    }

    public List<Project> getStaleTenders(int tick) {
        List<Project> staleTenders = new ArrayList<>();

        // Remove tenders that have been on the market for a long time
        for (Iterator<Project> iterator = getProjects().iterator(); iterator.hasNext();) {
            Project project = iterator.next();
            if (project.getEarnedValue() == 0 &&
                    project.getInvolvedPlayers().isEmpty() &&
                    project.getPublishedAt() + STALE_TENDERS_KILL_DAYS < tick) {
                // Add the tender to the list of stale tenders
                staleTenders.add(project);

                // Remove the current element from the iterator and the list
                iterator.remove();
            }
        }

        return staleTenders;
    }

    public void createProblemsInProjects(int tick, int level) {
        // In all running projectService.getProjects()...
        for (Project project : getProjects()) {
            // If it's not running or completed, skip to the next project
            if (project.getStartedAt() != 0 && !project.isCompleted()) {
                // For now, with a fixed chance for a problem to occur,
                // (can be adjusted later depending on project volume, risk level, etc.)
                double problemSpawnProbability = 0.01;
                // but not more than a certain number problems per project
                if (RANDOM.nextFloat() <= problemSpawnProbability
                        && project.getUnsolvedProblems().size() < level) { // Higher level -> more problems
                    // Take all problems of the project
                    List<Problem> occurredProblems = project.getProblems();

                    // Create a problem that has not occurred in the project before (independent of resolution state)
                    Problem problem = problemGenerator.generateRandomNewProblem(occurredProblems);
                    if (problem != null) { // Only add if a new problem is generated
                        // Add the problem to the project
                        problem.setOccurredAt(tick);
                        project.addProblem(problem);
                        logger.debug("New problem in project {} ({}): {}", project.getId(), project.getName(), problem.getTranslationKey());
                    }
                }
            }
        }
    }

    public void startStaleProjects(int tick) {
        // Don't do it for COMPLIANCE projects, independent of startedAt, acquiredAt etc.
        for (Project project : getProjects()) {
            if (project.getType() == ProjectType.COMPLIANCE) {
                continue;
            }

            // For all projects that have been acquired, but not started after MAX(30 days, 10% of project duration)
            if (project.getAcquiredAt() != 0 && project.getStartedAt() == 0) {
                int daysPassed = tick - project.getAcquiredAt();
                if (daysPassed >= max(30, project.getScheduledDuration() / 10)) {
                    // Start the project and inform involved players
                    startProject(project, tick - 1);
                    logger.debug("Project {} force started after {} days.", project.getName(), daysPassed);
                }
            }
        }
    }

    /**
     * Spawn easy and small project tenders for level 1.
     * @param tick     Current game tick
     * @param player   The player to spawn the project for
     */
    public void randomlySpawnLevel1Tenders(int tick, Player player) {
        // Don't spawn tenders until first mission is completed
        if (player.getMissions().getFirst().isNotCompleted()) {
            return;
        }

        // Have a chance to spawn a project
        if (RANDOM.nextFloat() >= PROJECT_SPAWN_PROBABILITY) {
            return;
        }

        Project project;
        // 25% chance for a perfect project
        if (RANDOM.nextFloat() <= 0.75) {
            // Low-risk, small projects
            project = new Project(RiskLevel.LOW).initialize();
        } else {
            // Find the project type and domain where one employee has the most experience
            Employee bestEmployee = player.getEmployees().stream().max(Comparator.
                    comparing(Employee::getExperience)).orElse(null);
            if (bestEmployee == null) {
                logger.error("No employee found for player {}.", player.getId());
                return;
            }
            String domain = bestEmployee.getDomainOfExpertise();
            ProjectType type = ProjectType.getTypeByDomain(domain);

            project = new Project(type, domain, RiskLevel.LOW, false);
        }

        project.setTenderProcess(false);
        project.setPublishedAt(tick);
        addProject(project);
    }

    public void randomlySpawnTenders(int tick) {
        if (RANDOM.nextFloat() <= PROJECT_SPAWN_PROBABILITY) {
            Project project = new Project().initialize();
            project.setPublishedAt(tick);
            addProject(project);
        }
    }

    public void loadProblems(int level) {
        problemGenerator.loadProblemsByLevel(level);
    }

    public void assessProjectRiskForPlayer(Project project, Player player, int tick, int level) {
        project.setHasBeenRiskAssessed(true);

        // Deduct funds from player
        int riskAssessmentCost = (int) GameParameters.PROJECT_RISK_ASSESSMENT_COST;
        accountingService.addEntry(new AccountingEntry(
                player,
                tick,
                level,
                riskAssessmentCost,
                AccountCategory.DEBIT_PROJECTS,
                TransactionType.DEBIT,
                "Project risk assessment")
        );
        player.subtractFunds(riskAssessmentCost);
    }

    @NotNull
    public ProjectSummary getProjectSummary(Project project) {
        ProjectSummary projectSummary = new ProjectSummary();
        projectSummary.setId(project.getId());
        projectSummary.setEarnedValue(project.getEarnedValue());
        return projectSummary;
    }

    public List<Project> getProjectsByPlayer(Player player) {
        List<Project> playerProjects = new ArrayList<>();
        for (Project project : getProjects()) {
            if (project.getInvolvedPlayers().contains(player)) {
                playerProjects.add(project);
            }
        }
        return playerProjects;
    }

    public Project getPreviousState(int projectId) {
        return previousProjectStates.get(projectId);
    }

    public void updatePreviousState(Project project) {
        previousProjectStates.put(project.getId(), deepCopy(project));
    }

    private Project deepCopy(Project project) {
        String json = gson.toJson(project);
        return gson.fromJson(json, Project.class);
    }

    public Optional<ProjectSummary> getProjectSummaryIfProgressChanged(Project project, int tick) {
        Project previousState = getPreviousState(project.getId());

        if (previousState == null || project.hasProgressChanged(previousState)) {
            updatePreviousState(project);

            ProjectSummary summary = getProjectSummary(project);
            summary.setTick(tick);

            return Optional.of(summary);
        }

        return Optional.empty();
    }
}