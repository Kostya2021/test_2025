package de.andrenitze.softpro.domains.employees;

import de.andrenitze.softpro.domains.employees.generators.EmployeeNameGenerator;
import de.andrenitze.softpro.domains.employees.utils.EmployeeUtils;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import de.andrenitze.softpro.domains.projects.Project;
import de.andrenitze.softpro.domains.projects.ProjectType;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;

import static de.andrenitze.softpro.GameServer.RANDOM;


@Slf4j //может убрать теперь? - добавил в 2 новых сервиса!?
public class Employee implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    public static final int MINIMUM_AGE = 20;//убрать - нет это оставить - это Неотъемлемое ограничение домена
    //вынес эти 2 нижнее константы тоже в сервис - StatusEffectService
//    public static final String PROJECT_MANAGEMENT_FOUNDATION = "Project Management Foundation";
//    public static final String PROJECT_MANAGEMENT_EXPERT = "Project Management Expert";
    private float sickDayProbability = 0.02f; //почему не константа? - уже не константа - она меняется в методах например addComplexStatuseffect и тд
    @Getter @Setter(AccessLevel.PACKAGE)
    private Integer id; //убрал временно final потому что нужно было сразу иниациализировать или через конструктор или сразу тут задавать значение - и только однократно можно - в project так же сделал
    @Getter
    private int salary; // monthly salary
    @Getter
    private final List<SalaryHistoryEntry> salaryHistory = new ArrayList<>();
    @Getter @Setter
    private int age;
    @Setter
    private String firstName;
    @Setter
    private String lastName;
    @Getter
    private final transient HashMap<Project, Integer> projectExperience = new HashMap<>(); // projectId and days XP
    @Getter private final EnumMap<ProjectType, Integer> projectTypeExperience = new EnumMap<>(ProjectType.class); //        //а что просто при инициализации она сразу не заполнится keys - ProjectType
    @Getter
    private final HashMap<String, Integer> projectDomainExperience = new HashMap<>();
    @Setter @Getter
    private float satisfaction;
    private int remainingAnnualSickDays;
//    @Getter
//    private static final int MINIMUM_SICK_DAYS = 4; //вроде нигде не используется вынес в employeeutil class
    private int maximumSickDays = 20; //почему не static? - потому что также как и sickDayProbability - она меняется в методах например addComplexStatuseffect и тд
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
    @Getter @Setter
    private int employedDays = 0;
    @Getter @Setter
    private int utilization = 0;
    @Getter @Setter
    private int xpInDaysBeforeHiring = 0;
    @Getter @Setter
    private boolean updated = false;

    //убрать констурктор как все проверю!!!
//    public Employee(Integer id) {
//        this.id = id;
//        String[] generatedName = EMPLOYEE_NAME_GENERATOR.generateName();
//        setFirstName(generatedName[0]);
//        setLastName(generatedName[1]);
//        setGender(generatedName[2]);
//
//        // Randomize salary
//        setSalary(RANDOM.nextInt(0, 1500) + 1500, 0);//второй аргумент 0 - это tick
//
//        // Randomize age between 20 and 60
//        setAge(RANDOM.nextInt(40) + MINIMUM_AGE);
//
//        this.satisfaction = EmployeeUtils.calculateSatisfaction(this.salary, this.age, this.statusEffects);
//
//        for (ProjectType type : ProjectType.values()) {
//            this.projectTypeExperience.put(type, 0);
//        }
//
//        // Add some days of experience in a few of the project types
//        int maxDaysOfXP = 365;
//        for (int i = 0; i < NUMBER_OF_PROJECTS_TO_HAVE_EXPERIENCE_IN; i++) {
//            int projectTypeIndex = RANDOM.nextInt(ProjectType.values().length);
//            int days = RANDOM.nextInt(maxDaysOfXP);
//
//            // Now, add some days of experience in one project domain of this type
//            ProjectType type = ProjectType.values()[projectTypeIndex];
//            addXp(type, type.getRandomDomain(), days);
//        }
//    }

    public Employee() {
    };


    //этот бы метод и остальные идущие ниже и связанные с опытом оставил в этом классе - только со своими полями работают - простая манипуляция данными
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

    private void addExperienceForDomain(String domain, int days) {
        if (projectDomainExperience.containsKey(domain)) {
            Integer existingDomainExperience = projectDomainExperience.getOrDefault(domain, 0);
            projectDomainExperience.put(domain, existingDomainExperience + days);
        } else {
            projectDomainExperience.put(domain, days);
        }
    }

    public Integer getExperience() {
        // Count the days of experience in all projects
        Integer totalExperience = 0;
        for (Integer experience : projectExperience.values()) {
            totalExperience += experience;
        }
        return totalExperience;
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


    //методы относящиеся к здоровью employee
    //этот метод рассчитывает будет ли employee болеть в этот tick
    //в нем сейчас переплетены 3 аспекта:
    //Прогресс «здоровья» (заразился/лечится)
    //Откат статус эффектов (cooldown) - то есть например есть employee доволен - то он работать будет проуктивнее и тд
    //Пересчёт загрузки (utilization) - сколько реально employee отработал с момента его трудоустройства
    //И второй момент ---> тут есть константа – sickDayProbability - вероятность заболевания
    //работает он со своими полями поэтому я бы его оставил тут но каждый из этих 3 аспектов вынес бы в свой атомарный метод а потом бы их вызывал в этом методе
    //остальные методы по здоровью я бы тут оставил - ниже которые идут
    public void liveLife(int currentTick) {
        employedDays++;

        if (!this.isSick()) { //например в 1-ый тик игры он точно не будет sick/болен - и заходим сюда
            if (this.remainingAnnualSickDays > 0 && RANDOM.nextDouble() <= sickDayProbability) {
                this.makeSick(true);
            }
        }
        else {
            haveSickLeaveDay(currentTick);//если уже болеет то сюда заходим
        }

        // Cooldown all status effects (if they have a cooldown)
        //эффекты - в него enum входит - поле - там 4 типа - SATISFACTION,HEALTH,PRODUCTIVITY,TRAINING
        //эффекты
        statusEffects.forEach(StatusEffect::cooldown);

        // Calculate utilization (0-100%)
        // 1) Calculate the number of days worked in projects - Получается общее число дней, проведённых сотрудником на проектах в рамках вашей организации - то есть у меня как игрока
        int daysWorkedInProjects = projectExperience.keySet().stream().mapToInt(projectExperience::get).sum();

        // 2) Subtract experience days before hiring
        //Поле xpInDaysBeforeHiring хранит, сколько дней опыта сотрудник уже имел до того, как пришёл в вашу компанию. Эти дни мы не считаем при вычислении текущей загрузки, потому что они не были «днями работы» на ваших проектах
        daysWorkedInProjects -= xpInDaysBeforeHiring;

        // 3) Divide daysWorkedInProjects (in this organization) by employedDays (in this organization)
        //employedDays — общее число тиков (дней) с момента приёма на работу - тот момент когда я его выбрал в игре на рынке
        //Делим “рабочие дни” на “общее время в компании” и умножаем на 100, чтобы получить процент - который сотрудник реально был загружен работой с момента найма
        utilization = (int) ((daysWorkedInProjects / (float) employedDays) * 100); //пока вообще никак не используется
    }

    //когда уже болеет - смотрим продолжит болеть или станет здоровым
    public void haveSickLeaveDay(int currentTick) {
        // Get better every day until fully recovered
        --remainingAnnualSickDays; //то есть мы уменьшим это поле только через тик как заболеем почему то
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

    public void makeSick(boolean sick) {
        isSick = sick;

        if (sick) {
            health = 0; // Decrease health to 0 when getting sick
        }
    }

    public boolean hasFirstDayAfterSickLeave(int currentTick) {
        return getLastSickDay() == currentTick - 1;
    }

    public void initializeSickDays(int annualSickDays) {
        this.remainingAnnualSickDays = annualSickDays;

        // Reset the number of sick days this year
        this.thisYearsSickDays = 0;
    }

    private void setLastSickDay(int currentTick) {
        lastSickDay = currentTick;
    }



    public void setSalary(int newSalary, int tick) {
        this.salary = newSalary;

        // Update salary history
        salaryHistory.add(new SalaryHistoryEntry(tick, newSalary));

        // Remove the oldest entry if the list is too long
        if (salaryHistory.size() > 100) { // Keep the last 100 entries
            salaryHistory.removeFirst();
        }

        //calculateSatisfaction(); было так
        this.satisfaction = EmployeeUtils.calculateSatisfaction(this.salary, this.age, this.statusEffects);
    }

    // Main method: adds a StatusEffect object if it does not already exist
    //методы пошли уже по третьей функицональной области - statusEffects!!!!!!!!!
    public boolean addStatusEffect(StatusEffect effect) {
        try {
            boolean alreadyExists = statusEffects.stream()
                    .anyMatch(e ->
                            e.getType() == effect.getType() &&
                                    Objects.equals(e.getDescription(), effect.getDescription()) &&
                                    Objects.equals(e.getTrigger(), effect.getTrigger())
                    );

            if (alreadyExists) {
                return false;
            }

            statusEffects.add(effect);
            log.debug("Added status effect '{}' - {} to {} ({} active effects)",
                    effect.getDescription(), effect.getType(), getName(), statusEffects.size());
            this.satisfaction = EmployeeUtils.calculateSatisfaction(this.salary, this.age, this.statusEffects);
            return true;
        }
        catch (Exception e) {
            log.error("Error adding status effect {} to {}: {}", effect, getName(), e.getMessage());
            return false;
        }
    }

    // Variant without cooldown (effect is permanent until removed)
    public void addStatusEffect(StatusEffectType effectType, float multiplier, String description) {
        addStatusEffect(new StatusEffect(effectType, multiplier, description));
    }

    // Variant with cooldown (effect is active for a certain number of ticks)
    public void addStatusEffect(StatusEffectType effectType, float multiplier, String description, int cooldown) {
        addStatusEffect(new StatusEffect(effectType, multiplier, description, cooldown));
    }

    // Variant without cooldown and multiplier (for trainings)
    public void addTraining(String training) {
        addStatusEffect(new StatusEffect(StatusEffectType.TRAINING,  training));

        // Add lost productivity status effect for training duration
        addStatusEffect(new StatusEffect(StatusEffectType.PRODUCTIVITY, 0.5f, "Training", 5));

    }

    //В IT и геймдеве crunch mode — это когда компания заставляет сотрудников работать сверхурочно, ночами, без выходных, чтобы успеть к релизу или дедлайну
    //Какие ещё сценарии могут появиться (и почему это “система”, а не Employee) --->
    //Мотивация бонусами — менеджер назначает премию, сотрудники временно счастливее
    //Сокращение — система решает: “Сотрудники теряют мотивацию, падает продуктивность
    //то есть тут внешний сценарий - это правила игры/компании - игрок сам не может решать
    //Эти сценарии не живут в Employee, потому что он их не придумывает, он просто получает их и реагирует
    //вынес бы пока в EmployeeServiceImpl если будет расти количество сценариев то тогда в StatusEffectService
//    public void addComplexStatusEffect(String effect) {
//        log.debug("Applying {} to {}", effect, getName());
//
//        // Effect "crunch-mode" will do: --->режим аврала/запары
//        // +50% productivity
//        // -20% satisfaction
//        // -10% health (absolute, recovers only slowly)
//        // Slightly increased chance of sick days
//        if (effect.equals(CRUNCH_MODE)) {
//            int cooldown = 20;
//            addStatusEffect(StatusEffectType.PRODUCTIVITY, 1.5f, CRUNCH_MODE, cooldown);
//            addStatusEffect(StatusEffectType.SATISFACTION, 0.8f, CRUNCH_MODE, cooldown);
//            addStatusEffect(StatusEffectType.HEALTH, 0.90f, CRUNCH_MODE, cooldown);
//
//            // Increment max and annual sick days with every "crunch mode", because it's stressful
//            remainingAnnualSickDays += 1;
//            maximumSickDays += 1;
//            sickDayProbability += 0.01f;
//        } else
//
//            // Effect "team-spirit" will do:
//            // -5% productivity (no cooldown = forever)
//            // +15% satisfaction (forever)
//            // +15% health (forever)
//            if (effect.equals(TEAM_SPIRIT)) {
//                addStatusEffect(StatusEffectType.PRODUCTIVITY, 0.95f, TEAM_SPIRIT);
//                addStatusEffect(StatusEffectType.SATISFACTION, 1.15f, TEAM_SPIRIT);
//                addStatusEffect(StatusEffectType.HEALTH, 1.15f, TEAM_SPIRIT);
//
//                // Decrease maximum sick days by 2 because of the positive effect on health
//                remainingAnnualSickDays -= 2;
//                maximumSickDays -= 2;
//            }
//    }

    //Это как если начальник вызвал тебя на разговор, выслушал твои проблемы, дал обратную связь.
    //После этого ты чувствуешь себя более ценным и довольным → работаешь охотнее.
    //тоже в сервис бы вынес EmployeeServiceImpl или StatusEffectService
//    public void haveOneToOneMeeting() {
//        // Don't add the same effect twice
//        statusEffects.removeIf(effect -> effect.getDescription().equals("Feels heard"));
//
//        // Add a time-limited status effect that increases satisfaction by 10% for some time
//        addStatusEffect(StatusEffectType.SATISFACTION, 1.1f, "Feels heard", 45);
//
//        this.satisfaction = EmployeeUtils.calculateSatisfaction(this.salary, this.age, this.statusEffects);
//
//    }

    //Когда на фронтенде игрок нажимает кнопку “Отправить сотрудника на обучение” - и тогда он на время тренинга теряет PRODUCTIVITY
    //тоже бы вынес в сервис - EmployeeServiceImpl или StatusEffectService или новый TrainingService
//    public void train(String training) {
//        // Add permanent status effect after the training
//        switch (training) {
//            case PROJECT_MANAGEMENT_FOUNDATION -> addTraining(PROJECT_MANAGEMENT_FOUNDATION);
//            case PROJECT_MANAGEMENT_EXPERT -> addTraining(PROJECT_MANAGEMENT_EXPERT);
//            default -> log.warn("Unknown training: {}", training);
//        }
//    }


    public void removeAllStatusEffects() {
        statusEffects.clear();
        log.debug("Removed all status effects from {}", getName());
        //calculateSatisfaction();
        this.satisfaction = EmployeeUtils.calculateSatisfaction(this.salary, this.age, this.statusEffects);

    }


    public void removeStatusEffectsByTrigger(Object trigger) {
        statusEffects.removeIf(effect -> {
            if (trigger != null && effect.getTrigger() == trigger && effect.getType() == StatusEffectType.SATISFACTION) {
                //calculateSatisfaction();
                this.satisfaction = EmployeeUtils.calculateSatisfaction(this.salary, this.age, this.statusEffects);
                log.debug("Removed status effects with trigger {} from {}", trigger.getClass(), getName());
            }
            return effect.getTrigger() == trigger;
        });
    }

    public boolean removeExpiredStatusEffects() {
        // Remove all expired status effects that are not infinite
        boolean removed = statusEffects.removeIf(StatusEffect::isExpiredAndNotInfinite);
        if (removed) {
            this.satisfaction = EmployeeUtils.calculateSatisfaction(this.salary, this.age, this.statusEffects);

        }
        return removed;
    }

    public void removeStatusEffectsByReason(String reason) {
        statusEffects.removeIf(effect -> {
            if (effect.getDescription().equals(reason) && effect.getType() == StatusEffectType.SATISFACTION) {
                //calculateSatisfaction();
                this.satisfaction = EmployeeUtils.calculateSatisfaction(this.salary, this.age, this.statusEffects);
            }
            return effect.getDescription().equals(reason);
        });
        log.debug("Removed status effects with reason {} from {}", reason, getName());
    }

    //добавил для сервиса statuseeffectservice
    public void increaseRemainingAnnualSickDays(int amount) {
        this.remainingAnnualSickDays += amount;
    }
    public void decreaseRemainingAnnualSickDays(int amount) {
        this.remainingAnnualSickDays -= amount;
    }

    public void increaseMaximumSickDays(int amount) {
        this.maximumSickDays += amount;
    }

    public void decreaseMaximumSickDays(int amount) {
        this.maximumSickDays -= amount;
    }

    public void increaseSickDayProbability(float amount) {
        this.sickDayProbability += amount;
    }





    public String getName() {
        return firstName + " " + lastName;
    }

    public Integer getExperienceByProject(Project project) {
        return projectExperience.getOrDefault(project, 0);
    }
    public Integer getExperienceByType(ProjectType type) {
        return projectTypeExperience.getOrDefault(type, 0);
    }

    public Integer getExperienceByDomain(String domain) {
        return projectDomainExperience.getOrDefault(domain, 0);
    }


    public Integer getExperienceInDaysByProjectType(ProjectType type) {
        return projectTypeExperience.get(type);
    }

    public Integer getExperienceInDaysByProjectDomain(String domain) {
        return projectDomainExperience.getOrDefault(domain, 0);
    }
}