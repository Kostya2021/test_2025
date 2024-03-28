package de.andrenitze.softpro;

import de.andrenitze.softpro.types.ProjectType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class Employee {
    public static final float SICK_DAY_PROBABILITY = 0.02f;
    public static final int NUMBER_OF_PROJECTS_TO_HAVE_EXPERIENCE_IN = 3;
    public static final int MINIMUM_SICK_DAYS = 4;
    public static final int MAXIMUM_SICK_DAYS = 22;
    private final Integer id;
    private final int salary;
    private final int age;
    private final String name;
    private final transient HashMap<Project, Integer> projectExperience;
    private final EnumMap<ProjectType, Integer> projectTypeExperience;
    private final HashMap<String, Integer> projectDomainExperience = new HashMap<>();
    private final int happiness;
    private int annualSickDays;
    private boolean isSick = false;
    private int lastSickDay = -1;
    private float health = 0.0f;
    private final String gender;

    Employee(Integer id) {
        this.name = generateName();
        this.gender = assignGender();

        // Randomize salary between 3000 and 4500
        this.salary = new Random().nextInt(0, 1500) + 3000;

        // Randomize age between 20 and 60
        this.age = new Random().nextInt(40) + 20;

        this.happiness = new Random().nextInt(45) + 50;
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
            int projectTypeIndex = new Random().nextInt(ProjectType.values().length);
            int days = new Random().nextInt(maxDaysOfXP);

            // Now, add some days of experience in one project domain of this type
            ProjectType type = ProjectType.values()[projectTypeIndex];
            addXp(type, type.getDomain(), days);
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

    private String assignGender() {
        if (new Random().nextFloat() <= 0.5) {
            return "male";
        } else {
            return "female";
        }
    }

    private String generateName() {
        String[] firstNames = {"Rahul","Siddharth","Rutuja","Neelam","Khushi","Ramanan","Pratik","Prashant","Arusha","Sasashy","Tanya","Priyanka","Deepak","Mahesh","Manoj","Naveen","Radhika","Krishna","Nishant","Priya","Adam","Alex","Aaron","Ben","Carl","Dan","David","Edward","Fred","Frank","George","Hal","Hank","Ike","John","Jack","Joe","Larry","Monte","Matthew","Mark","Nathan","Otto","Paul","Peter","Roger","Roger","Steve","Thomas","Tim","Ty","Victor","Walter","Olivia","Emma","Charlotte","Amelia","Ava","Sophia","Isabella","Mia","Evelyn","Harper","Luna","Camila","Gianna","Elizabeth","Eleanor","Ella","Abigail","Sofia","Avery","Scarlett","Emily","Aria","Penelope","Chloe","Layla","Mila","Nora","Hazel","Madison","Ellie","Lily","Nova","Isla","Grace","Violet","Aurora","Riley","Zoey","Willow","Emilia","Stella","Zoe","Victoria","Hannah","Addison","Leah","Lucy","Eliana","Ivy","Everly","Lillian","Paisley","Elena","Naomi","Maya","Natalie","Kinsley","Delilah","Claire","Audrey","Aaliyah","Ruby","Brooklyn","Alice","Aubrey","Autumn","Leilani","Savannah","Valentina","Kennedy","Madelyn","Josephine","Bella","Skylar","Genesis","Sophie","Hailey","Sadie","Natalia","Quinn","Caroline","Allison","Gabriella","Anna","Serenity","Nevaeh","Cora","Ariana","Emery","Lydia","Jade","Sarah","Eva","Adeline","Madeline","Piper","Rylee","Athena","Peyton","Everleigh"};
        String[] lastNames = {"Agarwal","Khatri","Ahuja","Anand","Patel","Babu","Balakrishnan","Banerjee","Varma","Dara","Chakrabarti","Deshpande","Gupta","Shah","Parekh","Singh","Modi","Acharya","Anderson","Ashwoon","Aikin","Bateman","Bongard","Bowers","Boyd","Cannon","Cast","Deitz","Dewalt","Ebner","Frick","Hancock","Haworth","Hesch","Hoffman","Kassing","Knutson","Lawless","Lawicki","Mccord","McCormack","Miller","Myers","Nugent","Ortiz","Orwig","Ory","Paiser","Pak","Pettigrew","Quinn","Quizoz","Ramachandran","Resnick","Sagar","Schickowski","Schiebel","Sellon","Severson","Shaffer","Solberg","Soloman","Sonderling","Soukup","Soulis","Stahl","Sweeney","Tandy","Trebil","Trusela","Trussel","Turco","Uddin","Uflan","Ulrich","Upson","Vader","Vail","Valente","Van Zandt","Vanderpoel","Ventotla","Vogal","Wagle","Wagner","Wakefield","Weinstein","Weiss","Woo","Yang","Yates","Yocum","Zeaser","Zeller","Ziegler","Bauer","Baxster","Casal","Cataldi","Caswell","Celedon","Chambers","Chapman","Christensen","Darnell","Davidson","Davis","DeLorenzo","Dinkins","Doran","Dugelman","Dugan","Duffman","Eastman","Ferro","Ferry","Fletcher","Fietzer","Hylan","Hydinger","Illingsworth","Ingram","Irwin","Jagtap","Jenson","Johnson","Johnsen","Jones","Jurgenson","Kalleg","Kaskel","Keller","Leisinger","LePage","Lewis","Linde","Lulloff","Maki","Martin","McGinnis","Mills","Moody","Moore","Napier","Nelson","Norquist","Nuttle","Olson","Ostrander","Reamer","Reardon","Reyes","Rice","Ripka","Roberts","Rogers","Root","Sandstrom","Sawyer","Schlicht","Schmitt","Schwager","Schutz","Schuster","Tapia","Thompson","Tiernan","Tisler" };
        return firstNames[new Random().nextInt(firstNames.length)] + " " + lastNames[(new Random().nextInt(firstNames.length))];
    }

    int getId() {
        return id;
    }

    int getSalary() {
        return salary;
    }

    int getProjectExperience() {
        int experience = 0;
        if (projectExperience != null) {
            for (Map.Entry<Project, Integer> entry : projectExperience.entrySet()) {
                Integer experiencePerProject = entry.getValue();
                experience += experiencePerProject;
            }
        }
        return experience;
    }

    Integer getExperienceInDaysByProject(Project project) {
        Integer experience = 0;
        if (projectExperience.get(project) != null) {
            experience = projectExperience.get(project);
        }
        return experience;
    }

    public String getName() {
        return name;
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
        health += new Random().nextFloat();

        if (health >= 1) {
            setSick(false);
            setLastSickDay(currentTick);
        }
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
            if (this.annualSickDays > 0 && new Random().nextDouble() <= SICK_DAY_PROBABILITY) {
                this.setSick(true);
            }
        } else {
            haveSickLeaveDay(currentTick);
        }
    }

    public void initializeSickDays() {
        this.annualSickDays = MINIMUM_SICK_DAYS + new Random().nextInt(MAXIMUM_SICK_DAYS - MINIMUM_SICK_DAYS);
    }

    public boolean hasFirstDayAfterSickLeave(int currentTick) {
        return getLastSickDay() == currentTick-1;
    }

    public int getLastSickDay() {
        return lastSickDay;
    }

    public void setLastSickDay(int lastSickDay) {
        this.lastSickDay = lastSickDay;
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
}
