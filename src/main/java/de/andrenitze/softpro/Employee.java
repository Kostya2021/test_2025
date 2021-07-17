package de.andrenitze.softpro;

import de.andrenitze.softpro.types.ProjectType;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class Employee {
    private static int lastId = 1;
    private final Integer id;
    private final int salary;
    private final int age;
    private final String name;
    private final HashMap<Project, Integer> projectExperienceInDays;
    private final HashMap<ProjectType, Float> experience;

    Employee() {
        this.name = generateName();
        this.salary = 3000;
        this.age = 30;
        this.id = lastId;
        this.experience = new HashMap<ProjectType, Float>();
        for (ProjectType type : ProjectType.values()) {
            this.experience.put(type, 0.0f);
        }
        ++lastId;
        projectExperienceInDays = new HashMap<>();
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

    int getProjectExperienceInDays() {
        int experience = 0;
        if (projectExperienceInDays != null){
            for (Map.Entry<Project, Integer> entry : projectExperienceInDays.entrySet()) {
                Integer experiencePerProject = entry.getValue();
                experience += experiencePerProject;
            }
        }
        return experience;
    }

    Integer getExperienceInDays(Project project) {
        Integer experience = 0;
        if (projectExperienceInDays.get(project) != null) {
            experience = projectExperienceInDays.get(project);
        }
        return experience;
    }

    public String getName() {
        return name;
    }

    public void addExperience(Project project, Float newDays) {
        Integer rampUpDays = 0;
        if (this.projectExperienceInDays.containsKey(project)) {
            rampUpDays = this.projectExperienceInDays.get(project);
        }
        this.projectExperienceInDays.put(project, ++rampUpDays);

        if (newDays > 0) {
            Float existingDays = this.experience.get(project.getType());
            this.experience.put(project.getType(), existingDays + newDays);
        }
    }

    public Float getExperience(ProjectType type) {
        return experience.get(type);
    }
}
