package de.andrenitze.softpro;

import de.andrenitze.softpro.types.ProjectType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class Employee {
    public static final float SICK_DAY_PROBABILITY = 0.02f;
    private static int lastId = 1;
    public static final int MINIMUM_SICK_DAYS = 4;
    public static final int MAXIMUM_SICK_DAYS = 22;
    private final Integer id;
    private final int salary;
    private final int age;
    private final String name;
    private final HashMap<Project, Integer> projectExperience;
    private final EnumMap<ProjectType, Integer> projectTypeExperience;
    private final HashMap<String, Integer> projectDomainExperience = new HashMap<>();
    private final int happiness;
    private int annualSickDays;
    private boolean isSick = false;
    private int lastSickDay = -1;
    private float health = 0.0f;

    Employee() {
        this.name = generateName();
        this.salary = 3000;
        this.age = 30;
        this.happiness = 60;
        this.id = lastId;
        initializeSickDays();
        ++lastId;

        this.projectExperience = new HashMap<>();
        this.projectTypeExperience = new EnumMap<>(ProjectType.class);
        for (ProjectType type : ProjectType.values()) {
            this.projectTypeExperience.put(type, 0);
        }
    }

    private String generateName() {
        String[] firstNames = {"Rahul","Siddharth", "Rutuja", "Neelam", "Khushi", "Ramanan", "Pratik", "Prashant", "Arusha", "Sasashy", "Tanya", "Priyanka", "Deepak", "Mahesh", "Manoj", "Naveen", "Radhika", "Krishna", "Nishant", "Priya", "Adam", "Alex", "Aaron", "Ben", "Carl", "Dan", "David", "Edward", "Fred", "Frank", "George", "Hal", "Hank", "Ike", "John", "Jack", "Joe", "Larry", "Monte", "Matthew", "Mark", "Nathan", "Otto", "Paul", "Peter", "Roger", "Roger", "Steve", "Thomas", "Tim", "Ty", "Victor", "Walter"};
        String[] lastNames = {"Agarwal", "Khatri", "Ahuja", "Anand", "Patel", "Babu", "Balakrishnan", "Banerjee", "Varma", "Dara", "Chakrabarti", "Deshpande", "Gupta", "Shah", "Parekh", "Singh", "Modi", "Acharya", "Anderson", "Ashwoon", "Aikin", "Bateman", "Bongard", "Bowers", "Boyd", "Cannon", "Cast", "Deitz", "Dewalt", "Ebner", "Frick", "Hancock", "Haworth", "Hesch", "Hoffman", "Kassing", "Knutson", "Lawless", "Lawicki", "Mccord", "McCormack", "Miller", "Myers", "Nugent", "Ortiz", "Orwig", "Ory", "Paiser", "Pak", "Pettigrew", "Quinn", "Quizoz", "Ramachandran", "Resnick", "Sagar", "Schickowski", "Schiebel", "Sellon", "Severson", "Shaffer", "Solberg", "Soloman", "Sonderling", "Soukup", "Soulis", "Stahl", "Sweeney", "Tandy", "Trebil", "Trusela", "Trussel", "Turco", "Uddin", "Uflan", "Ulrich", "Upson", "Vader", "Vail", "Valente", "Van Zandt", "Vanderpoel", "Ventotla", "Vogal", "Wagle", "Wagner", "Wakefield", "Weinstein", "Weiss", "Woo", "Yang", "Yates", "Yocum", "Zeaser", "Zeller", "Ziegler", "Bauer", "Baxster", "Casal", "Cataldi", "Caswell", "Celedon", "Chambers", "Chapman", "Christensen", "Darnell", "Davidson", "Davis", "DeLorenzo", "Dinkins", "Doran", "Dugelman", "Dugan", "Duffman", "Eastman", "Ferro", "Ferry", "Fletcher", "Fietzer", "Hylan", "Hydinger", "Illingsworth", "Ingram", "Irwin", "Jagtap", "Jenson", "Johnson", "Johnsen", "Jones", "Jurgenson", "Kalleg", "Kaskel", "Keller", "Leisinger", "LePage", "Lewis", "Linde", "Lulloff", "Maki", "Martin", "McGinnis", "Mills", "Moody", "Moore", "Napier", "Nelson", "Norquist", "Nuttle", "Olson", "Ostrander", "Reamer", "Reardon", "Reyes", "Rice", "Ripka", "Roberts", "Rogers", "Root", "Sandstrom", "Sawyer", "Schlicht", "Schmitt", "Schwager", "Schutz", "Schuster", "Tapia", "Thompson", "Tiernan", "Tisler" };
        return firstNames[new Random().nextInt(firstNames.length)] + " " + lastNames[(new Random().nextInt(firstNames.length))];
    }

    int getId() {
        return id;
    }

    int getSalary() {
        return salary;
    }

    int getAge() {
        return age;
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

            // Project-type-specific XP
            Integer existingExperience = this.projectTypeExperience.getOrDefault(project.getType(), 0);
            this.projectTypeExperience.put(project.getType(), existingExperience + newExperienceInDays);

            // Domain-specific XP
            Integer existingDomainExperience = this.projectDomainExperience.getOrDefault(project.getDomain(), 0);
            this.projectDomainExperience.put(project.getDomain(), existingDomainExperience + newExperienceInDays);
        }
    }

    public Integer getExperienceInDaysByProjectType(ProjectType type) {
        return projectTypeExperience.get(type);
    }

    public Integer getExperienceInDaysByProjectDomain(String domain) {
        return projectDomainExperience.get(domain);
    }

    public Integer getHappiness() {
        return happiness;
    }

    public int getSickDays() {
        return annualSickDays;
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
}
