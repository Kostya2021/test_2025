package de.andrenitze.softpro;

import de.andrenitze.softpro.config.GameParameters;
import de.andrenitze.softpro.domains.accounting.AccountCategory;
import de.andrenitze.softpro.domains.accounting.AccountingEntry;
import de.andrenitze.softpro.domains.accounting.AccountingService;
import de.andrenitze.softpro.domains.accounting.TransactionType;
import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.projects.Project;
import de.andrenitze.softpro.events.GameEvent;
import de.andrenitze.softpro.events.EventType;
import de.andrenitze.softpro.domains.projects.ProjectType;
import de.andrenitze.softpro.domains.employees.StatusEffect;
import de.andrenitze.softpro.domains.employees.StatusEffectType;
import lombok.Getter;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static de.andrenitze.softpro.Game.*;
import static de.andrenitze.softpro.events.GameEventHandler.PARTY_CLIENT;
import static de.andrenitze.softpro.GameServer.gson;
import static java.lang.Math.*;

public class ProjectService {
    public static final double CONTRACTOR_CANCELLATION_PENALTY = 0.15;  // 15% penalty when contractor cancels (kill dead horse)
    public static final double CLIENT_CANCELLATION_PENALTY = 0.3;      // 30% penalty when client cancels (due to delay)
    private final Game game;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final SkillsManager skillsManager;
    private final ConcurrentHashMap<Project, ArrayList<Employee>> projectEmployeesMap;
    private final AccountingService accountingService;
    @Getter
    private ArrayList<Project> projects = new ArrayList<>();

    private static final String EVENT_TYPE = "type";
    private static final String FAMILIARIZATION_WITH_NEW_DOMAIN = "Familiarization with new project domain";
    private static final String FAMILIARIZATION_WITH_NEW_TYPE = "Familiarization with new project type";

    public ProjectService(Game game) {
        this.game = game;
        this.skillsManager = game.getSkillsManager();
        this.projectEmployeesMap = game.getProjectEmployeesMap();
        this.accountingService = game.getAccountingService();
    }

    public void conductWorkOnAllProjects(int currentTick, LocalDate currentDate,
                                         ConcurrentHashMap<Project, ArrayList<Employee>> projectEmployeesMap) {
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

            addEarnedValueForEachEmployee(project, employees, currentTick);

            JSONObject event = new JSONObject();
            event.put(EVENT_TYPE, EventType.PROJECT_UPDATED);
            var projectObject = new JSONObject();

            // Finish the project
            if (project.isCompleted()) {
                // Remove all status effects on employees related to this project
                projectEmployeesMap.get(project).forEach(employee -> {
                    employee.removeStatusEffectsByDescription(FAMILIARIZATION_WITH_NEW_DOMAIN);
                    employee.removeStatusEffectsByDescription(FAMILIARIZATION_WITH_NEW_TYPE);
                });

                // Send reward
                handleProjectCompletion(project, employees, currentTick, projectObject);

                // Remove the project from employees map, so that employees are unassigned
                project.setEarnedValue(project.getTotalValue());
                iterator.remove();
            }

            // Build a small custom event to just send new project progress and success metrics
            projectObject.put("id", project.getId());
            projectObject.put("earnedValue", project.getEarnedValue());
            projectObject.put("tick", currentTick);

            if (project.isCompleted()) {
                projectObject.put("completedAt", currentTick);
            }

            event.put("payload", projectObject);

            // Send update to all involved players
            for (Player player : project.getInvolvedPlayers()) {
                game.getMessagingService().sendMessageToPlayer(player, event.toString());
            }
        }
    }

    private void handleProjectCompletion(Project project, ArrayList<Employee> employees, int currentTick, JSONObject projectObject) {
        for (Player player : project.getInvolvedPlayers()) {
            int profit = (int) round(project.getTotalValue() * Game.PROFIT_MARGIN);

            float overduePenaltyMultiplier = 1;
            int daysLeft = project.getDeadline() - (currentTick - project.getStartedAt());
            if (daysLeft < 0) {
                // Per 1% delayed delivery, return 2% less win margin
                overduePenaltyMultiplier = 1 - ((float) Math.abs(daysLeft) / project.getDeadline() * 2);
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
                game.sendEmployeeUpdate(player, employee);
            }
        }

        // Calculate project result quality (0-100)
        calculateProjectQuality(project, employees, projectObject);
    }

    private void calculateProjectQuality(Project project, ArrayList<Employee> employees, JSONObject projectObject) {
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

        int earnedValue;

        // Rule #3: Adding people to a late software project makes it later (Brooks' law)
        // New employees will decrease the whole team's productivity for on-boarding and training
        float onboardingFactor;
        if (!project.isRampingUp(currentTick) && project.hasOnboardingEmployees(employees)) {
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

            // Rule x?: No one ever gains experience in compliance projects, so productivity is the same for everyone
            if (project.getType() == ProjectType.COMPLIANCE) {
                earnedValue = (int)(earnedValue * 0.8);
                project.addEarnedValue(earnedValue, currentTick);
                return;
            }

            // Experience in days for project type and domain
            int typeXP = employee.getExperienceInDaysByProjectType(project.getType());
            int domainXP = employee.getExperienceInDaysByProjectDomain(project.getDomain());

            float productivityFactor = calculateProductivityFactor(typeXP, domainXP);

            earnedValue = (int) (earnedValue * productivityFactor * 2);

            // Rule #1: Context changes decrease employee productivity.
            int numberOfParallelProjects = getNumberOfParallelProjectsForEmployee(employee);
            earnedValue /= (numberOfParallelProjects == 0) ? 1 : numberOfParallelProjects;
            switch (numberOfParallelProjects) {
                case 1 -> earnedValue = (int) (earnedValue * 1.0);
                case 2 -> earnedValue = (int) (earnedValue * 0.4);
                case 3 -> earnedValue = (int) (earnedValue * 0.2);
                case 4 -> earnedValue = (int) (earnedValue * 0.1);
                case 5 -> earnedValue = (int) (earnedValue * 0.05);
                default -> earnedValue = 1;
            }

            // Rule #2: Productivity ramp-up: New staff in project needs some time to get fully productive.
            float x = employee.getExperienceInDaysByProject(project);
            if (x < 30) {
                float rampUpProductivityFactor = (float) (1.022595 - 1.02502 * exp(-0.1399307 * x));
                earnedValue = (int) (earnedValue * rampUpProductivityFactor);
            }

            // Increase the employee's experience
            employee.gainExperience(project, 1);

            earnedValue = (int) (earnedValue * onboardingFactor);

            if (project.getEarnedValue() == 0 && earnedValue > 0) {
                project.setStartedAt(currentTick);
            }

            // Rule #5: Organizational skills affect productivity.
            earnedValue = (int) (earnedValue * calculateSkillsFactor(project));

            // Rule #6: Employees are affected by external status effects
            if (!employee.getStatusEffects().isEmpty()) {
                for (StatusEffect effect : employee.getStatusEffects()) {
                    if (effect.getType() == StatusEffectType.PRODUCTIVITY) {
                        earnedValue = (int) (earnedValue * effect.getMultiplier());
                    }
                }
            }

            // Rule #7: Productivity is decreased by unsolved problems in projects
            if (!project.getUnsolvedProblems().isEmpty()) {
                // For each unsolved problem, that has been lingering for some time, add a penalty of 25% to productivity
                int gracePeriod = 30;

                // Count lingering projects
                int unsolvedLingeringProblems = (int) project.getUnsolvedProblems().stream()
                        .filter(problem -> currentTick - problem.getOccurredAt() > gracePeriod)
                        .count();

                // Add the productivity penalty
                earnedValue = (int) (earnedValue * pow(0.75, unsolvedLingeringProblems));
            }

            // Increase the project's earnedValue for this employee
            project.addEarnedValue(earnedValue, currentTick);
        }
    }

    private float calculateProductivityFactor(int typeXP, int domainXP) {
        // Weights for project type and domain experience
        float typeXpWeight = 0.25f;
        float domainXpWeight = 0.75f;

        // Base productivity value (if experience = 0)
        float baseProductivity = 0.25f;

        return (float) (baseProductivity +
                (1 - baseProductivity) * (
                        typeXpWeight * (1 - Math.exp(-0.001 * typeXP)) +
                                domainXpWeight * (1 - Math.exp(-0.001 * domainXP))
                ));
    }

    private float calculateSkillsFactor(Project project) {
        List<Player> players = project.getInvolvedPlayers();

        // If only one player is working on the project
        if (players.size() == 1) {
            Player player = players.get(0);
            if (skillsManager.playerHasSkill(player, "pmo")) {
                return 1.05f;
            }
        }
        return 1.0f;
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

        for (Map.Entry<Project, ArrayList<Employee>> entry : projectEmployeesMap.entrySet()) {
            ArrayList<Employee> employees = entry.getValue();
            Project project = entry.getKey();

            if (employees.contains(employee) && project.hasBeenStarted()) {
                numberOfProjects++;
            }
        }
        return numberOfProjects;
    }

    public void assignEmployeeToProject(Employee employee, Project project) {
        if (isNull(employee, project)) return;

        // Get current list of employees working on that project
        try {
            projectEmployeesMap.putIfAbsent(project, new ArrayList<>());
            ArrayList<Employee> employees = projectEmployeesMap.get(project);

            // Add employee to project if not already assigned
            if (!employees.contains(employee)) {
                employees.add(employee);
                projectEmployeesMap.put(project, employees);
                logger.debug("{} assigned to {}", employee.getName(), project.getName());
            }
        } catch (NullPointerException e) {
            logger.error(e.toString());
        }
    }

    public void removeEmployeeFromProject(Employee employee, Project project) {
        if (isNull(employee, project)) return;

        // Get current list of employees working on that project
        ArrayList<Employee> employees = projectEmployeesMap.get(project) ;

        if (employees.contains(employee)) {
            employees.remove(employee);
            projectEmployeesMap.put(project, employees);
            logger.debug("{} unassigned from {}", employee.getName(), project.getName());
        }
    }

    private boolean isNull(Employee employee, Project project) {
        if (project == null || employee == null) {
            logger.error("Project or employee is null.");
            return true;
        }
        return false;
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
            logger.error("Player {} is not involved in project {}.", player.getName(), project.getName());
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
        if (projectEmployeesMap.containsKey(project)) {
            projectEmployeesMap.get(project).clear();
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
            projectEmployeesMap.remove(project);
        }

        logger.debug("Project {} cancelled by player {}", project.getName(), player.getId());
    }

    public void cancelOverdueProjects(int currentTick) {
        List<Project> projectsToCancel = new ArrayList<>();

        // Identify projects that meet the cancellation criteria
        for (Project project : getProjects()) {
            // Check if project should be automatically cancelled
            if (shouldAutomaticallyCancel(project, currentTick)) {
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

    public void setProjects(ArrayList<Object> objects) {
        for (Object object : objects) {
            if (object instanceof Project) {
                projects.add((Project) object);
            }
        }
    }

    public void addProject(Project project) {
        // Check if project id already exists, if not, add the project
        if (getProjectById(project.getId()) == null) {
            projects.add(project);
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
}