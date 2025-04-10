package de.andrenitze.softpro.domains.employees;

import de.andrenitze.softpro.domains.projects.Project;
import de.andrenitze.softpro.domains.projects.ProjectType;
import lombok.Getter;
import lombok.Setter;

import java.beans.Transient;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;

import static de.andrenitze.softpro.GameServer.RANDOM;
import static de.andrenitze.softpro.Main.logger;
import static de.andrenitze.softpro.events.GameEventHandler.CRUNCH_MODE;
import static de.andrenitze.softpro.events.GameEventHandler.TEAM_SPIRIT;

public class Employee implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    public static final int NUMBER_OF_PROJECTS_TO_HAVE_EXPERIENCE_IN = 3;
    public static final int MINIMUM_AGE = 20;
    public static final int JOB_SATISFACTION = 50;
    private float sickDayProbability = 0.02f;
    @Getter
    private final Integer id;
    @Getter
    private int salary; // monthly salary
    @Getter
    private final List<SalaryHistoryEntry> salaryHistory = new ArrayList<>();
    @Setter
    private int age;
    @Setter
    private String firstName;
    @Setter
    private String lastName;
    @Getter(onMethod_=@Transient)
    private final transient HashMap<Project, Integer> projectExperience; // projectId and days XP
    @Getter
    private final EnumMap<ProjectType, Integer> projectTypeExperience;
    @Getter
    private final HashMap<String, Integer> projectDomainExperience = new HashMap<>();
    @Setter @Getter
    private float satisfaction;
    private int remainingAnnualSickDays;
    @Getter
    private static final int MINIMUM_SICK_DAYS = 4;
    private int maximumSickDays = 20;
    @Getter
    private boolean isSick = false;
    @Getter
    private int lastSickDay = -1;
    @Getter
    private final NavigableMap<Integer, Boolean> sickDays = new TreeMap<>(); // tick -> True (if sick)
    @Getter @Setter
    private int hiredAt = -1;
    @Getter @Setter
    private int thisYearsSickDays = 0;
    @Getter @Setter
    private float health = 0.0f;
    @Getter @Setter
    private String gender;
    @Getter
    private final List<StatusEffect> statusEffects = new ArrayList<>();
    private static final NameGenerator nameGenerator = NameGenerator.getInstance();
    @Getter @Setter
    private int employedDays = 0;
    @Getter @Setter
    private int utilization = 0;
    @Getter @Setter
    private int xpInDaysBeforeHiring = 0;

    public Employee(Integer id) {
        this.id = id;
        String[] generatedName = nameGenerator.generateName();
        setFirstName(generatedName[0]);
        setLastName(generatedName[1]);
        setGender(generatedName[2]);

        // Randomize salary
        setSalary(RANDOM.nextInt(0, 1500) + 1500, 0);

        // Randomize age between 20 and 60
        setAge(RANDOM.nextInt(40) + MINIMUM_AGE);

        calculateSatisfaction();
        initializeSickDays();

        this.projectExperience = new HashMap<>();
        this.projectTypeExperience = new EnumMap<>(ProjectType.class);
        for (ProjectType type : ProjectType.values()) {
            this.projectTypeExperience.put(type, 0);
        }

        // Add some days of experience in a few of the project types
        int maxDaysOfXP = 365;
        for (int i = 0; i < NUMBER_OF_PROJECTS_TO_HAVE_EXPERIENCE_IN; i++) {
            int projectTypeIndex = RANDOM.nextInt(ProjectType.values().length);
            int days = RANDOM.nextInt(maxDaysOfXP);

            // Now, add some days of experience in one project domain of this type
            ProjectType type = ProjectType.values()[projectTypeIndex];
            addXp(type, type.getRandomDomain(), days);
        }
    }

    private void addExperienceForDomain(String domain, int days) {
        if (projectDomainExperience.containsKey(domain)) {
            Integer existingDomainExperience = projectDomainExperience.getOrDefault(domain, 0);
            projectDomainExperience.put(domain, existingDomainExperience + days);
        } else {
            projectDomainExperience.put(domain, days);
        }
    }

    public Integer getExperienceByProject(Project project) {
        return projectExperience.getOrDefault(project, 0);
    }

    /**
     * Employee gains experience in a project.
     * XP in days is stored in projectExperience AND projectTypeExperience AND projectDomainExperience.
     */
    public void gainExperience(Project project, int newExperienceInDays) {
        // Don't gain experience in compliance projects
        if (project.getType() == ProjectType.COMPLIANCE) {
            return;
        }

        if (newExperienceInDays > 0) {
            int existingExperience = this.projectExperience.computeIfAbsent(project, ignored -> 0);
            this.projectExperience.put(project, ++existingExperience);

            addXp(project.getType(), project.getDomain(), newExperienceInDays);
        }
    }

    public Integer getExperienceInDaysByProjectType(ProjectType type) {
        return projectTypeExperience.get(type);
    }

    public Integer getExperienceInDaysByProjectDomain(String domain) {
        return projectDomainExperience.getOrDefault(domain, 0);
    }

    public void haveSickLeaveDay(int currentTick) {
        // Get better every day until fully recovered
        --remainingAnnualSickDays;
        setHealth(getHealth() + RANDOM.nextFloat());

        if (getHealth() >= 1) {
            setHealth(1f);
            makeSick(false);
            setLastSickDay(currentTick);
        }

        // Mark current tick as a sick day
        sickDays.put(currentTick, true);

        // Increment the number of sick days this year
        thisYearsSickDays++;
    }

    private void setLastSickDay(int currentTick) {
        lastSickDay = currentTick;
    }


    public void makeSick(boolean sick) {
        isSick = sick;

        if (sick) {
            health = 0; // Decrease health to 0 when getting sick
        }
    }

    public void liveLife(int currentTick) {
        employedDays++;

        if (!this.isSick()) {
            if (this.remainingAnnualSickDays > 0 && RANDOM.nextDouble() <= sickDayProbability) {
                this.makeSick(true);
            }
        } else {
            haveSickLeaveDay(currentTick);
        }

        // Cooldown all status effects (if they have a cooldown)
        statusEffects.forEach(StatusEffect::cooldown);

        // Calculate utilization (0-100%)
        // 1) Calculate the number of days worked in projects
        int daysWorkedInProjects = projectExperience.keySet().stream().mapToInt(projectExperience::get).sum();

        // 2) Subtract experience days before hiring
        daysWorkedInProjects -= xpInDaysBeforeHiring;

        // 3) Divide daysWorkedInProjects (in this organization) by employedDays (in this organization)
        utilization = (int) ((daysWorkedInProjects / (float) employedDays) * 100);
    }

    // This happens every year
    public void initializeSickDays() {
        // Randomize the number of sick days an employee can have in a year
        this.remainingAnnualSickDays = MINIMUM_SICK_DAYS + RANDOM.nextInt(maximumSickDays - MINIMUM_SICK_DAYS);

        // Reset the number of sick days this year
        this.thisYearsSickDays = 0;
    }

    public boolean hasFirstDayAfterSickLeave(int currentTick) {
        return getLastSickDay() == currentTick - 1;
    }

    public void addXp(ProjectType type, String domain, int days) {
        // Check if the project domain is valid
        if (domain == null || domain.isEmpty()) {
            throw new IllegalArgumentException("Domain must be one of the following: " + projectDomainExperience.keySet());
        }

        // Check if the project type is valid
        if (type == null) {
            throw new IllegalArgumentException("Type must be one of the following: " + projectTypeExperience.keySet());
        }

        // Check if the number of days is valid
        if (days < 0) {
            throw new IllegalArgumentException("Number of days must be positive");
        }

        // XP is always added for type and domain because a domain always belongs to exactly one type
        addExperienceForType(type, days);
        addExperienceForDomain(domain, days);
    }

    private void addExperienceForType(ProjectType type, int days) {
        Integer existingExperience = projectTypeExperience.getOrDefault(type, 0);
        projectTypeExperience.put(type, existingExperience + days);
    }

    public void setSalary(int newSalary, int tick) {
        this.salary = newSalary;

        // Update salary history
        salaryHistory.add(new SalaryHistoryEntry(tick, newSalary));

        // Remove the oldest entry if the list is too long
        if (salaryHistory.size() > 100) { // Keep the last 100 entries
            salaryHistory.removeFirst();
        }

        calculateSatisfaction();
    }

    private void calculateSatisfaction() {
        double salaryInThousands = this.salary / 1000.0;
        double otherSatisfactionFactors = calculateOtherSatisfactionFactors();
        double baseSatisfaction = calculateBaseSatisfaction();

        // Calculate the salary component
        double salaryComponent = (Math.log(salaryInThousands) * 30 + Math.sqrt(salaryInThousands) * 20);
        salaryComponent = Math.min(salaryComponent, 100);

        // Weighted components
        double salaryWeight = 0.5;
        double factorsWeight = 0.5;

        // Calculate total satisfaction
        this.satisfaction = (float) ((salaryWeight * salaryComponent) + (factorsWeight * otherSatisfactionFactors) + baseSatisfaction);

        // Apply all status effects of type SATISFACTION
        for (StatusEffect effect : statusEffects) {
            if (effect.getType() == StatusEffectType.SATISFACTION) {
                this.satisfaction *= effect.getMultiplier();
            }
        }

        this.satisfaction = Math.clamp(this.satisfaction, 1, 100); // Clamp to [1, 100]
    }

    // Intrinsic satisfaction of an employee
    private double calculateBaseSatisfaction() {
        // Value between 5 and 20, depending on age
        return Math.clamp(20L - (age - MINIMUM_AGE), 5, 20);
    }

    // Job satisfaction factors not related to salary
    private int calculateOtherSatisfactionFactors() {
        // Dummy value, refine later (work environment, career opportunities, mentoring etc.)
        // Satisfaction 0-100
        return JOB_SATISFACTION;
    }

    public String getName() {
        return firstName + " " + lastName;
    }

    public void addStatusEffect(StatusEffect effect) {
        statusEffects.add(effect);
        logger.debug("Added status effect '{}' - {} to {} ({} active effects)", effect.getDescription(), effect.getType(), getName(), statusEffects.size());
        calculateSatisfaction();
    }

    // Variant without cooldown (effect is permanent until removed)
    public void addStatusEffect(StatusEffectType effectType, float multiplier, String description) {
        addStatusEffect(new StatusEffect(effectType, multiplier, description));
    }

    // Variant with cooldown (effect is active for a certain number of ticks)
    public void addStatusEffect(StatusEffectType effectType, float multiplier, String description, int cooldown) {
        addStatusEffect(new StatusEffect(effectType, multiplier, description, cooldown));
    }

    public Integer getExperience() {
        // Count the days of experience in all projects
        Integer totalExperience = 0;
        for (Integer experience : projectExperience.values()) {
            totalExperience += experience;
        }
        return totalExperience;
    }

    public Integer getExperienceByType(ProjectType type) {
        return projectTypeExperience.getOrDefault(type, 0);
    }

    public Integer getExperienceByDomain(String domain) {
        return projectDomainExperience.getOrDefault(domain, 0);
    }

    public String getDomainOfExpertise() {
        // Find the domain with the most experience
        Map.Entry<String, Integer> maxEntry = null;
        for (Map.Entry<String, Integer> entry : projectDomainExperience.entrySet()) {
            if (maxEntry == null || entry.getValue().compareTo(maxEntry.getValue()) > 0) {
                maxEntry = entry;
            }
        }
        if (maxEntry == null) {
            return null;
        }
        return maxEntry.getKey();
    }

    public void removeAllStatusEffects() {
        statusEffects.clear();
        logger.debug("Removed all status effects from {}", getName());
        calculateSatisfaction();
    }

    public void addComplexStatusEffect(String effect) {
        logger.debug("Applying {} to {}", effect, getName());

        // Effect "crunch-mode" will do:
        // +50% productivity
        // -20% satisfaction
        // -10% health (absolute, recovers only slowly)
        // Slightly increased chance of sick days
        if (effect.equals(CRUNCH_MODE)) {
            int cooldown = 20;
            addStatusEffect(StatusEffectType.PRODUCTIVITY, 1.5f, CRUNCH_MODE, cooldown);
            addStatusEffect(StatusEffectType.SATISFACTION, 0.8f, CRUNCH_MODE, cooldown);
            addStatusEffect(StatusEffectType.HEALTH, 0.90f, CRUNCH_MODE, cooldown);

            // Increment max and annual sick days with every "crunch mode", because it's stressful
            remainingAnnualSickDays += 1;
            maximumSickDays += 1;
            sickDayProbability += 0.01f;
        } else

            // Effect "team-spirit" will do:
            // -5% productivity (no cooldown = forever)
            // +15% satisfaction (forever)
            // +15% health (forever)
            if (effect.equals(TEAM_SPIRIT)) {
                addStatusEffect(StatusEffectType.PRODUCTIVITY, 0.95f, TEAM_SPIRIT);
                addStatusEffect(StatusEffectType.SATISFACTION, 1.15f, TEAM_SPIRIT);
                addStatusEffect(StatusEffectType.HEALTH, 1.15f, TEAM_SPIRIT);

                // Decrease maximum sick days by 2 because of the positive effect on health
                remainingAnnualSickDays -= 2;
                maximumSickDays -= 2;
            }
    }

    public boolean removeExpiredStatusEffects() {
        boolean removed = statusEffects.removeIf(StatusEffect::isExpired);
        if (removed) {
            calculateSatisfaction();
        }
        return removed;
    }

    public void haveOneToOneMeeting() {
        // Don't add the same effect twice
        statusEffects.removeIf(effect -> effect.getDescription().equals("Feels heard"));

        // Add a time-limited status effect that increases satisfaction by 10% for some time
        addStatusEffect(StatusEffectType.SATISFACTION, 1.1f, "Feels heard", 45);

        calculateSatisfaction();
    }

    public void removeStatusEffectsByTrigger(Object trigger) {
        statusEffects.removeIf(effect -> {
            if (effect.getTrigger() == trigger && effect.getType() == StatusEffectType.SATISFACTION) {
                calculateSatisfaction();
            }
            return effect.getTrigger() == trigger;
        });
        logger.debug("Removed status effects with trigger {} from {}", trigger.getClass(), getName());
    }

    public void removeStatusEffectsByReason(String reason) {
        statusEffects.removeIf(effect -> {
            if (effect.getDescription().equals(reason) && effect.getType() == StatusEffectType.SATISFACTION) {
                calculateSatisfaction();
            }
            return effect.getDescription().equals(reason);
        });
        logger.debug("Removed status effects with reason {} from {}", reason, getName());
    }
}