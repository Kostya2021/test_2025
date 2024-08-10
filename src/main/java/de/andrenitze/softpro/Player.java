package de.andrenitze.softpro;

import de.andrenitze.softpro.entities.Objective;
import de.andrenitze.softpro.entities.Objectives;
import de.andrenitze.softpro.types.Decision;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

import static de.andrenitze.softpro.GameServer.RANDOM;
import static de.andrenitze.softpro.Main.logger;

public class Player {
    private static final HashMap<Integer, Float> INITIAL_FUNDS = new HashMap<>() {{
        put(1, 18000f);
        put(2, 150000f);
        put(3, 1000f);
        put(4, 150000f);
        put(5, 150000f);
        put(6, 150000f);
        put(7, 150000f);
    }};
    private static final HashMap<Integer, Integer> BANKRUPTCY_THRESHOLD = new HashMap<>() {{
        put(1, -500);
        put(2, -100000);
        put(3, 0);
        put(4, 0);
        put(5, 0);
        put(6, 0);
        put(7, 0);
    }};
    @Getter
    private final UUID id;
    private String name;
    @Getter @Setter
    private String firstName;
    @Getter @Setter
    private String lastName;
    @Getter @Setter
    private String company;
    @Getter @Setter
    private float funds;
    @Getter @Setter
    private ArrayList<Employee> employees = new ArrayList<>();
    @Getter @Setter
    private ArrayList<Objective> objectives;
    @Getter @Setter
    private boolean ready;
    @Setter
    private int xp = 0;
    @Getter @Setter
    private int skillPoints = 1;
    @Setter
    @Getter
    private int level = 1;

    @EqualsAndHashCode.Exclude
    private Map<Integer, List<Decision>> decisions = new HashMap<>();

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

        return adjective + " " + subject;
    }

    Player(String name, String company) {
        setName(name);

        this.company = company;
        this.id = UUID.randomUUID();
        initializeObjectives();
    }

    String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;

        // Best guess name splitting
        this.firstName = name.split(" ")[0];

        // Prevent errors from one-word names
        if (name.split(" ").length < 2) {
            this.lastName = "";
            return;
        }
        this.lastName = name.split(" ")[1];
    }

    public float addFunds(float additionalFunds) {
        this.funds += additionalFunds;
        return funds;
    }

    void subtractFunds(float fundsToSubtract) {
        this.funds -= fundsToSubtract;
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

    /**
     * This method loads objectives and funds for the next level.
     * It requires the player's <i>level</i> to be set correctly before calling the method!
     */
    public void initializeObjectives() {
        this.objectives = Objectives.getObjectivesForLevel(this.level);

        // Make sure all objectives are not completed
        this.objectives.forEach(objective -> objective.setCompleted(false));

        logger.debug("Loaded funds and {} objectives for level {} and player {}", this.objectives.size(), this.level, id);
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

    public void addEmployee(Employee employee) {
        this.employees.add(employee);
    }

    public void removeEmployee(Employee employee) {
        this.employees.remove(employee);
    }

    public void setDecisions(int level, List<Decision> decisions) {
        this.decisions.put(level, decisions);
    }

    public void initializeFunds() {
        this.funds = INITIAL_FUNDS.get(this.level);
    }

    public boolean completedAllObjectives() {
        for (Objective objective : objectives) {
            if (!objective.isCompleted()) {
                return false;
            }
        }
        return true;
    }

    public boolean isBankrupt() {
        return this.funds < BANKRUPTCY_THRESHOLD.get(this.level);
    }

    public List<Decision> getDecisionsByLevel(int level) {
        return decisions.get(level);
    }
}
