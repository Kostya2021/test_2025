package de.andrenitze.softpro;

import de.andrenitze.softpro.entities.NameGenerator;
import de.andrenitze.softpro.types.ProjectType;
import de.andrenitze.softpro.types.StatusEffect;
import de.andrenitze.softpro.types.StatusEffectType;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

import static de.andrenitze.softpro.GameServer.RANDOM;
import static de.andrenitze.softpro.Main.logger;

public class Employee {
    public static final float SICK_DAY_PROBABILITY = 0.02f;
    public static final int NUMBER_OF_PROJECTS_TO_HAVE_EXPERIENCE_IN = 3;
    public static final int MINIMUM_SICK_DAYS = 4;
    public static final int MAXIMUM_SICK_DAYS = 22;
    public static final int MINIMUM_AGE = 20;
    private final Integer id;
    private int salary; // monthly salary
    @Setter
    private int age;
    @Setter
    private String firstName;
    @Setter
    private String lastName;
    @Getter
    private final transient HashMap<Project, Integer> projectExperience;
    @Getter
    private final EnumMap<ProjectType, Integer> projectTypeExperience;
    @Getter
    private final HashMap<String, Integer> projectDomainExperience = new HashMap<>();
    @Setter @Getter
    private float satisfaction;
    private int annualSickDays;
    private boolean isSick = false;
    private int lastSickDay = -1;
    private float health = 0.0f;
    @Getter
    private String gender;
    @Getter
    private List<StatusEffect> statusEffects = new ArrayList<>();
    private static final NameGenerator nameGenerator = NameGenerator.getInstance();

    Employee(Integer id) {
        String[] generatedName = nameGenerator.generateName();
        this.firstName = generatedName[0];
        this.lastName = generatedName[1];
        this.gender = generatedName[2];

        // Randomize salary between 3000 and 4500
        this.salary = RANDOM.nextInt(0, 1500) + 3000;

        // Randomize age between 20 and 60
        this.age = RANDOM.nextInt(40) + MINIMUM_AGE;

        calculateSatisfaction();
        this.id = id;
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

    int getId() {
        return id;
    }

    int getSalary() {
        return salary;
    }

    Integer getExperienceInDaysByProject(Project project) {
        Integer experience = 0;
        if (projectExperience.get(project) != null) {
            experience = projectExperience.get(project);
        }
        return experience;
    }

    public void gainExperience(Project project, Integer newExperienceInDays) {

        if (newExperienceInDays > 0) {
            // Project-specific XP (= lower onboarding productivity)
            Integer rampUpDays = 0;
            if (this.projectExperience.containsKey(project)) {
                rampUpDays = this.projectExperience.get(project);
            }
            this.projectExperience.put(project, ++rampUpDays);

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
        --annualSickDays;
        health += RANDOM.nextFloat();

        if (health >= 1) {
            setSick(false);
            setLastSickDay(currentTick);
        }
    }

    private void setLastSickDay(int currentTick) {
        lastSickDay = currentTick;
    }

    public boolean isSick() {
        return isSick;
    }

    public void setSick(boolean sick) {
        isSick = sick;

        if (sick) {
            health = 0;
        }
    }

    public void beAtWork(int currentTick) {
        if (!this.isSick()) {
            if (this.annualSickDays > 0 && RANDOM.nextDouble() <= SICK_DAY_PROBABILITY) {
                this.setSick(true);
            }
        } else {
            haveSickLeaveDay(currentTick);
        }
    }

    public void initializeSickDays() {
        this.annualSickDays = MINIMUM_SICK_DAYS + RANDOM.nextInt(MAXIMUM_SICK_DAYS - MINIMUM_SICK_DAYS);
    }

    public boolean hasFirstDayAfterSickLeave(int currentTick) {
        return getLastSickDay() == currentTick-1;
    }

    private int getLastSickDay() {
        return lastSickDay;
    }

    public void addXp(ProjectType type, String domain, int days) {
        // Check if the project domain is valid
        if (domain == null || domain.isEmpty() ) {
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

    public void setSalary(int i) {
        this.salary = i;

        // Change happiness based on salary (with diminishing returns)
        calculateSatisfaction();
    }

    private void calculateSatisfaction() {
        double salaryInThousands = this.salary / 1000.0;
        double otherSatisfactionFactors = calculateOtherSatisfactionFactors();
        double baseHappiness = calculateBaseHappiness();

        // Calculate the salary component
        double salaryComponent = (Math.log(salaryInThousands) * 30 + Math.sqrt(salaryInThousands) * 20);
        salaryComponent = Math.min(salaryComponent, 100);

        // Weighted components
        double salaryWeight = 0.5;
        double factorsWeight = 0.5;

        // Calculate total satisfaction
        this.satisfaction = (float) ((salaryWeight * salaryComponent) + (factorsWeight * otherSatisfactionFactors) + baseHappiness);

        // Apply all status effects of type SATISFACTION
        for (StatusEffect effect : statusEffects) {
            if (effect.getType() == StatusEffectType.SATISFACTION) {
                this.satisfaction *= effect.getMultiplier();
            }
        }

        this.satisfaction = (int) Math.min(Math.max(this.satisfaction, 1), 100); // Clamp to [1, 100]
    }

    // Intrinsic happiness of an employee
    private double calculateBaseHappiness() {
        // Value between 5 and 20, depending on age
        return Math.min(20, Math.max(5, 20 - (age - MINIMUM_AGE)));
    }

    // Job satisfaction factors not related to salary
    private int calculateOtherSatisfactionFactors() {
        // Dummy value, refine later (work environment, career opportunities, mentoring etc.)
        // Satisfaction 0-100
        return 50;
    }

    public String getName() {
        return firstName + " " + lastName;
    }

    public void setStatusEffect(StatusEffect effect) {
        statusEffects.add(effect);
        calculateSatisfaction();
    }

    public void setStatusEffect(StatusEffectType effectType, float multiplier, String description) {
        setStatusEffect(new StatusEffect(effectType, multiplier, description));
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

    public void clearStatusEffectsByType(StatusEffectType type) {
        statusEffects.removeIf(effect -> effect.getType() == type);
        calculateSatisfaction();
    }
}
