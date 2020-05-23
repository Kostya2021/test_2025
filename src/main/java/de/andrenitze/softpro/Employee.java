package de.andrenitze.softpro;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class Employee {
    private static int lastId = 0;
    private final int id;
    private final int salary;
    private final int age;
    private final String name;
    private final HashMap<Project, Integer> experienceInDays;

    Employee() {
        this.name = generateName();
        this.salary = 3000;
        this.age = 30;
        this.id = lastId;
        ++lastId;
        experienceInDays = new HashMap<>();
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

    int getExperienceInDays() {
        int experience = 0;
        if (experienceInDays != null){
            for (Map.Entry<Project, Integer> entry : experienceInDays.entrySet()) {
                Integer experiencePerProject = entry.getValue();
                experience += experiencePerProject;
            }
        }
        return experience;
    }

    Integer getExperienceInDays(Project project) {
        Integer experience = 0;
        if (experienceInDays.get(project) != null) {
            experience = experienceInDays.get(project);
        }
        return experience;
    }

    public String getName() {
        return name;
    }

    void increaseExperience(Project project) {
        Integer experienceToBeAdded = 1;

        // First call; Add the project key and the initial value (1)
        if (!experienceInDays.containsKey(project)) {
            experienceInDays.put(project, experienceToBeAdded);
        } else {
            // All subsequent calls; Increase the experience
            Integer newExperience = experienceInDays.get(project) + experienceToBeAdded;
            experienceInDays.put(project, newExperience);
        }
    }
}
