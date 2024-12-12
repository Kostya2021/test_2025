package de.andrenitze.softpro;

import de.andrenitze.softpro.entities.Mission;
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
    private List<Mission> missions;
    @Getter @Setter
    private boolean ready;

    // Experience points (XP) are gained by completing projects
    @Getter @Setter
    private int xp = 0;

    // XP level is calculated based on the XP points and thresholds defined in SkillsManager
    @Getter @Setter
    private int xpLevel = 0;

    // Skill points are acquired after gaining a certain amount of XP and are invested to unlock skills
    @Getter @Setter
    private int skillPoints = 0;
    // The level the player has reached in the game
    @Setter @Getter
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

    public Player(String name, String company) {
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

    public List<Objective> getNewObjectivesByTick(int tick) {
        List<Objective> newObjectives = new ArrayList<>();
        for (Mission mission : this.missions) {
            if (!mission.isCompleted() && (mission.getEarliestOccurrence() == tick || (mission.getEarliestOccurrence() == 0 && !mission.isProcessed()))) {
                boolean canAddObjectives = true;
                for (Mission m : this.missions) {
                    if (m.getOrder() < mission.getOrder() && !m.isCompleted()) {
                        canAddObjectives = false;
                        break;
                    }
                }
                if (canAddObjectives) {
                    for (Objective objective : mission.getObjectives()) {
                        if (!objective.isCompleted()) {
                            objective.setMission(mission.getTitle()); // Helper attribute for the frontend
                            newObjectives.add(objective);
                        }
                    }
                    mission.setProcessed(true);
                }
            }
        }
        return newObjectives;
    }

    // Returns objectives until the given tick, but depending on order.
    public List<Objective> getObjectivesUntilThisTick(int tick) {
    List<Objective> allActiveObjectives = new ArrayList<>();
    for (Mission mission : this.missions) {
        if (mission.getEarliestOccurrence() == 0 || mission.getEarliestOccurrence() <= tick) {
            boolean canAddObjectives = true;
            for (Mission m : this.missions) {
                // Order: Objectives in a mission with "order == 2" will only be shown
                // if all objectives in a mission with "order == 1" are completed.
                if (m.getOrder() < mission.getOrder() && !m.isCompleted()) {
                    canAddObjectives = false;
                    break;
                }
            }
            if (canAddObjectives) {
                for (Objective objective : mission.getObjectives()) {
                    objective.setMission(mission.getTitle()); // Only for the frontend
                    allActiveObjectives.add(objective);
                }
            }
        }
    }
    return allActiveObjectives;
}

    public List<Objective> getCompletedObjectives() {
        List<Objective> completedObjectives = new ArrayList<>();
        for (Mission mission : this.missions) {
            for (Objective objective : mission.getObjectives()) {
                if (objective.isCompleted()) {
                    completedObjectives.add(objective);
                }
            }
        }
        return completedObjectives;
    }

    /**
     * This method loads objectives and funds for the next level.
     * It requires the player's <i>level</i> to be set correctly before calling the method!
     */
    public void initializeObjectives() {
        this.missions = Objectives.getInstance(this.level).getMissions();

        // Make sure all objectives are not completed
        for (Mission mission : this.missions) {
            for (Objective objective : mission.getObjectives()) {
                objective.setCompleted(false);
            }
        }

        logger.debug("Loaded funds and {} missions for level {} and player {}", this.missions.size(), this.level, id);
    }

    public void addXp(int newXP) {
        // Check if the new XP level exceeds an XP_LEVEL_THRESHOLD and increase the xpLevel if necessary
        int xpToLevelUp = SkillsManager.XP_LEVEL_THRESHOLDS[this.xpLevel];

        if (this.xp + newXP >= xpToLevelUp) {
            this.xp += newXP; // Add the new XP
            this.xpLevel++; // Level up
            this.skillPoints++; // Receive a skill point when leveling up
        } else {
            this.xp += newXP; // Only add the new XP
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
        for (Mission mission : this.missions) {
            for (Objective objective : mission.getObjectives()) {
                if (!objective.isCompleted()) {
                    return false;
                }
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