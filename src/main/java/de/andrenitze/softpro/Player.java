package de.andrenitze.softpro;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.andrenitze.softpro.entities.Objective;
import de.andrenitze.softpro.entities.Objectives;

import java.util.ArrayList;
import java.util.Random;
import java.util.UUID;

public class Player {
    private static final Random RANDOM = new Random();
    public static final float INITIAL_FUNDS = 150000;

    @JsonIgnore
    private final UUID id;

    @JsonProperty
    private String name;

    @JsonProperty
    private String company;

    @JsonProperty
    private float funds = INITIAL_FUNDS;

    @JsonProperty
    private ArrayList<Employee> employees = new ArrayList<>();

    @JsonIgnore
    private ArrayList<Objective> objectives;

    @JsonIgnore
    private boolean ready;

    @JsonProperty
    private int xp = 0;
    @JsonProperty
    private int skillPoints = 5;

    @JsonProperty
    private int level = 0;


    Player() {
        this(generatePlayerName(), generateCompanyName());
    }

    private static String generateCompanyName() {
        String[] techyWords = new String[]{"Software","TechNik","Soft","MacroMedium","Midio","SPLNK","MARGO","CouchDemo"};
        return techyWords[RANDOM.nextInt(techyWords.length)] + " Inc.";
    }

    private static String generatePlayerName() {
        String[] adjectives = new String[]{"aggressive","agreeable","ambitious","brave","calm","delightful","eager","faithful","gentle","happy","jolly","kind","lively","nice","obedient","polite","proud","silly","thankful","victorious","witty","wonderful","zealous"};
        String[] subjects = new String[]{"Giraffe","Woodpecker","Camel","Starfish","Koala","Alligator","Owl","Tiger","Bear","Blue","whale","Coyote","Chimpanzee","Raccoon","Lion","Arctic","Wolf","Crocodile","Dolphin","Elephant","Squirrel","Snake","Kangaroo","Hippopotamus","Elk","Fox","Gorilla","Bat","Hare","Toad","Frog","Deer","Rat","Badger","Lizard","Mole","Hedgehog","Otter","Reindeer"};

        // Capitalize both words to create a full name
        String adjective = adjectives[RANDOM.nextInt(adjectives.length)];
        adjective = adjective.substring(0, 1).toUpperCase() + adjective.substring(1);

        String subject = subjects[RANDOM.nextInt(subjects.length)];
        subject = subject.substring(0, 1).toUpperCase() + subject.substring(1);

        return adjective + subject + RANDOM.nextInt(99);
    }

    Player(String name, String company) {
        this.name = name;
        this.company = company;
        this.id = UUID.randomUUID();
        initializeBeforeGame();
    }

    String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public float addFunds(float additionalFunds) {
        this.funds += additionalFunds;
        return funds;
    }

    void subtractFunds(float fundsToSubtract) {
        this.funds -= fundsToSubtract;
    }

    public float getFunds() {
        return funds;
    }

    ArrayList<Employee> getEmployees() {
        return employees;
    }

    void calculateAndSubtractSalaries() {
        employees.forEach(employee -> this.subtractFunds(employee.getSalary()));
    }

    Employee getEmployeeById(int id) {
        for (Employee employee : employees) {
            if (employee.getId() == id) {
                return employee;
            }
        }
        return null;
    }

    public UUID getId() {
        return id;
    }

    public ArrayList<Objective> getObjectives() {
        return objectives;
    }

    public ArrayList<Objective> getNewObjectivesForThisTick(int tick) {
        ArrayList<Objective> allObjectives = this.getObjectives();
        ArrayList<Objective> newObjectivesForThisTick = new ArrayList<>();
        allObjectives.forEach(objective -> {
            if (objective.getEarliestOccurrence() == tick) {
                newObjectivesForThisTick.add(objective);
            }
        });
        return newObjectivesForThisTick;
    }
    public ArrayList<Objective> getActiveObjectivesUntilThisTick(int tick) {
        ArrayList<Objective> allObjectives = getObjectives();
        ArrayList<Objective> allActiveObjectives = new ArrayList<>();
        allObjectives.forEach(objective -> {
            if (objective.getEarliestOccurrence() == 0 ||
                    objective.getEarliestOccurrence() <= tick) {
                allActiveObjectives.add(objective);
            }
        });
        return allActiveObjectives;
    }

    public ArrayList<Objective> getCompletedObjectives() {
        ArrayList<Objective> completedObjectives = new ArrayList<>();
        getObjectives().forEach(objective -> {
            if (objective.isCompleted()) {
                completedObjectives.add(objective);
            }
        });
        return completedObjectives;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public void initializeBeforeGame() {
        setReady(false);
        this.funds = INITIAL_FUNDS;

        Objectives objectives = new Objectives();
        objectives.loadObjectivesFromYamlFile();
        this.objectives = objectives.getObjectives();
    }

    public void addXp(int newXP) {
        this.xp += newXP;

        // Check if new XP is enough to level up
        int xpToLevelUp = SkillsManager.LEVEL_THRESHOLDS[this.level];

        if (this.xp >= xpToLevelUp) {
            this.skillPoints++;
            this.level++;
        }
    }

    public int getSkillPoints() {
        return skillPoints;
    }

    public void setSkillPoints(int skillPoints) {
        this.skillPoints = skillPoints;
    }

    public void addEmployee(Employee employee) {
        this.employees.add(employee);
    }

    public void removeEmployee(Employee employee) {
        this.employees.remove(employee);
    }
}
