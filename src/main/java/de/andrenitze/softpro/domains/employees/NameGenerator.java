package de.andrenitze.softpro.domains.employees;

import static de.andrenitze.softpro.GameServer.RANDOM;

public class NameGenerator {
    public static final String FEMALE = "female";
    public static final String MALE = "male";
    private static NameGenerator instance;
    private String gender;

    private NameGenerator() {
        // Prevent instantiation
    }

    public static synchronized NameGenerator getInstance() {
        if (instance == null) {
            instance = new NameGenerator();
        }
        return instance;
    }

    private void randomlyAssignGender() {
        if (RANDOM.nextFloat() <= 0.5) {
            this.gender = MALE;
        } else {
            this.gender = FEMALE;
        }
    }

    // Constants for region indices
    private static final int REGION_ASIA = 0;
    private static final int REGION_AFRICA = 1;
    private static final int REGION_EUROPE = 2;
    private static final int REGION_LATIN_AMERICA = 3;
    private static final int REGION_NORTH_AMERICA = 4;
    private static final int REGION_OCEANIA = 5;

    // Names by region and gender - static to avoid recreating these large arrays on each call
    private static final String[][] MALE_NAMES_BY_REGION = initializeMaleNamesByRegion();
    private static final String[][] FEMALE_NAMES_BY_REGION = initializeFemaleNamesByRegion();
    private static final String[][] UNISEX_NAMES_BY_REGION = initializeUnisexNamesByRegion();
    private static final String[][] LAST_NAMES_BY_REGION = initializeLastNamesByRegion();

    // Global population distribution
    private static final float[] REGION_DISTRIBUTION = {0.595f, 0.172f, 0.096f, 0.084f, 0.048f, 0.005f};

    private static String[][] initializeMaleNamesByRegion() {
        String[][] names = new String[6][];

        // Asia
        names[REGION_ASIA] = new String[] {
            "Rahul", "Siddharth", "Ramanan", "Pratik", "Prashant", "Deepak", "Mahesh",
            "Manoj", "Naveen", "Krishna", "Nishant", "Rajesh", "Rajiv", "Rakesh",
            "Ramesh", "Ravi", "Rohit", "Sachin", "Sandeep", "Sanjay", "Santosh",
            "Satish", "Shankar", "Shashi", "Shiv", "Shyam", "Siddharth", "Sudhir",
            "Sunil", "Suresh", "Umesh", "Vikram", "Vinay", "Vivek", "Yogesh"
        };

        // Africa
        names[REGION_AFRICA] = new String[] {
            "Kwame", "Kofi", "Abdul", "Adebayo", "Chinedu", "Juma", "Kwasi", "Chukwu",
            "Sekou", "Ibrahim", "Amadou", "Tunde", "Yusuf", "Olufemi", "Kofi", "Femi",
            "Obi", "Moses", "Emeka", "Ifeanyi"
        };

        // Europe
        names[REGION_EUROPE] = new String[] {
            "John", "Paul", "George", "Matthew", "Mark", "James", "Robert", "Charles",
            "Michael", "William", "Thomas", "David", "Richard", "Edward", "Henry",
            "Arthur", "Peter", "Simon", "Christopher", "Daniel"
        };

        // Latin America
        names[REGION_LATIN_AMERICA] = new String[] {
            "Carlos", "Juan", "Miguel", "Jose", "Luis", "Alejandro", "Diego", "Fernando",
            "Santiago", "Manuel", "Francisco", "Jorge", "Pedro", "Ricardo", "Andres",
            "Antonio", "Rafael", "Hector", "Gabriel", "Eduardo"
        };

        // North America
        names[REGION_NORTH_AMERICA] = new String[] {
            "Adam", "Ben", "Carl", "David", "Edward", "James", "John", "Robert",
            "Michael", "William", "Joseph", "Charles", "Thomas", "Daniel", "Christopher",
            "Matthew", "Andrew", "George", "Richard", "Henry"
        };

        // Oceania
        names[REGION_OCEANIA] = new String[] {
            "James", "William", "Lucas", "Noah", "Liam", "Oliver", "Ethan",
            "Mason", "Alexander", "Benjamin", "Henry", "Jack", "Leo", "Thomas",
            "Sebastian", "Harrison", "Levi", "Oscar", "Isaac", "Charlie"
        };

        return names;
    }

    private static String[][] initializeFemaleNamesByRegion() {
        String[][] names = new String[6][];

        // Asia
        names[REGION_ASIA] = new String[] {
            "Rutuja", "Neelam", "Khushi", "Arusha", "Tanya", "Priyanka", "Radhika",
            "Priya", "Pooja", "Neha", "Kajal", "Jyoti", "Lata", "Sangeeta", "Meena",
            "Sunita", "Alka", "Swati", "Kiran", "Rekha", "Rupali", "Anjali", "Sushma",
            "Nandini", "Smita", "Shilpa", "Anita", "Aarti", "Kavita", "Geeta"
        };

        // Africa
        names[REGION_AFRICA] = new String[] {
            "Fatima", "Aisha", "Zainab", "Nkechi", "Chimamanda", "Ngozi", "Yasmin",
            "Amara", "Adama", "Nala", "Chinyere", "Abena", "Sade", "Aminata", "Ayodele",
            "Bintu", "Halima", "Isioma", "Lulu", "Oluchi"
        };

        // Europe
        names[REGION_EUROPE] = new String[] {
            "Olivia", "Emma", "Charlotte", "Amelia", "Ava", "Sophia", "Isabella",
            "Mia", "Evelyn", "Harper", "Luna", "Ella", "Grace", "Sophie", "Chloe",
            "Lily", "Ruby", "Hannah", "Alice", "Lucy"
        };

        // Latin America
        names[REGION_LATIN_AMERICA] = new String[] {
            "Sofia", "Isabella", "Camila", "Valentina", "Josefina", "Gabriela", "Daniela",
            "Maria", "Carolina", "Fernanda", "Luisa", "Paula", "Adriana", "Veronica",
            "Carmen", "Victoria", "Renata", "Andrea", "Claudia", "Monica"
        };

        // North America
        names[REGION_NORTH_AMERICA] = new String[] {
            "Emily", "Abigail", "Madison", "Grace", "Violet", "Sophia", "Olivia",
            "Ava", "Emma", "Isabella", "Mia", "Charlotte", "Harper", "Luna",
            "Ella", "Amelia", "Sofia", "Aria", "Scarlett", "Penelope"
        };

        // Oceania
        names[REGION_OCEANIA] = new String[] {
            "Ava", "Isabella", "Mia", "Evelyn", "Harper", "Amelia", "Charlotte",
            "Olivia", "Sophia", "Grace", "Emily", "Lily", "Zoe", "Hannah",
            "Lucy", "Scarlett", "Ruby", "Ella", "Isla", "Chloe"
        };

        return names;
    }

    private static String[][] initializeUnisexNamesByRegion() {
        String[][] names = new String[6][];

        // Asia
        names[REGION_ASIA] = new String[] {
            "Sasashy", "Arya", "Devan", "Kiran", "Ritu", "Nisha", "Rohan", "Samir",
            "Vishal", "Dev", "Anil", "Sujata", "Laxmi", "Suman", "Poonam", "Ashok",
            "Bhaskar", "Chandan", "Gagan", "Indra"
        };

        // Africa
        names[REGION_AFRICA] = new String[] {
            "Chidi", "Amari", "Kamau", "Omari", "Zuri", "Eshe", "Imani", "Kato",
            "Sanyu", "Biko", "Nuru", "Ayo", "Jelani", "Malik", "Tariq", "Mosi",
            "Kito", "Nyah", "Shani", "Tari"
        };

        // Europe
        names[REGION_EUROPE] = new String[] {
            "Alex", "Charlie", "Sam", "Taylor", "Jordan", "Morgan", "Casey", "Riley",
            "Jamie", "Avery", "Rowan", "Quinn", "Drew", "Elliot", "Emerson", "Finley",
            "Harper", "Peyton", "Reese", "Sage"
        };

        // Latin America
        names[REGION_LATIN_AMERICA] = new String[] {
            "Adrian", "Alex", "Andrea", "Cruz", "Emilio", "Francisco", "Jaime",
            "Julian", "Marcos", "Nico", "Paz", "Roberto", "Salvador", "Santiago",
            "Tomas", "Vicente", "Xavier", "Yago", "Zoe", "Alejandra"
        };

        // North America
        names[REGION_NORTH_AMERICA] = new String[] {
            "Riley", "Jordan", "Taylor", "Morgan", "Casey", "Alex", "Jamie",
            "Avery", "Rowan", "Reese", "Drew", "Harper", "Quinn", "Peyton",
            "Sage", "Bailey", "Dakota", "Cameron", "Skyler", "Hunter"
        };

        // Oceania
        names[REGION_OCEANIA] = new String[] {
            "Charlie", "Riley", "Jordan", "Taylor", "Morgan", "Alex", "Casey",
            "Jamie", "Avery", "Rowan", "Reese", "Dakota", "Skyler", "Sage",
            "Bailey", "Cameron", "Harley", "Drew", "Quinn", "Peyton"
        };

        return names;
    }

    private static String[][] initializeLastNamesByRegion() {
        String[][] names = new String[6][];

        // Asia
        names[REGION_ASIA] = new String[] {
            "Patel", "Shah", "Gupta", "Singh", "Modi", "Rao", "Desai", "Chowdhury",
            "Reddy", "Iyer", "Pillai", "Naidu", "Bhattacharya", "Das", "Mehta",
            "Gandhi", "Sharma", "Verma", "Kapoor", "Jain"
        };

        // Africa
        names[REGION_AFRICA] = new String[] {
            "Okeke", "Abebe", "Hussein", "Diop", "Adebayo", "Nwosu", "Oluoch",
            "Kamara", "Mugabe", "Nkosi", "Adesina", "Omotayo", "Onyango",
            "Makinde", "Mbatha", "Thompson", "Banda", "Sibanda", "Kumalo", "Ngoma"
        };

        // Europe
        names[REGION_EUROPE] = new String[] {
            "Smith", "Johnson", "Brown", "Taylor", "Anderson", "Miller", "Davis",
            "Wilson", "Moore", "Clark", "Thompson", "White", "Hall", "Allen",
            "Harris", "Lewis", "Walker", "Robinson", "Wood", "Wright"
        };

        // Latin America
        names[REGION_LATIN_AMERICA] = new String[] {
            "Gomez", "Rodriguez", "Lopez", "Hernandez", "Martinez", "Garcia",
            "Gonzalez", "Perez", "Sanchez", "Ramirez", "Torres", "Flores",
            "Rivera", "Cruz", "Reyes", "Mendoza", "Morales", "Ortiz", "Gutierrez", "Ramos"
        };

        // North America
        names[REGION_NORTH_AMERICA] = new String[] {
            "Miller", "Davis", "Garcia", "Martinez", "Robinson", "Anderson",
            "Wilson", "Moore", "Taylor", "Thomas", "Jackson", "White", "Harris",
            "Martin", "Thompson", "Lee", "Perez", "Walker", "Hall", "Young"
        };

        // Oceania
        names[REGION_OCEANIA] = new String[] {
            "Smith", "Williams", "Brown", "Wilson", "Taylor", "Jones", "Martin",
            "Lee", "Harris", "Clark", "Robinson", "Walker", "White", "Thompson",
            "Hall", "King", "Wright", "Scott", "Green", "Baker"
        };

        return names;
    }

    /**
     * Determines the region index based on probability distribution
     */
    private int selectRegion() {
        float rand = RANDOM.nextFloat();
        float cumulativeProbability = 0.0f;

        for (int i = 0; i < REGION_DISTRIBUTION.length; i++) {
            cumulativeProbability += REGION_DISTRIBUTION[i];
            if (rand < cumulativeProbability) {
                return i;
            }
        }

        return REGION_OCEANIA; // Default to Oceania (least likely)
    }

    /**
     * Gets the appropriate first names array based on gender and region
     */
    private String[] getFirstNamesByGenderAndRegion(String gender, int region) {
        if (gender.equals(MALE)) {
            return MALE_NAMES_BY_REGION[region];
        } else if (gender.equals(FEMALE)) {
            return FEMALE_NAMES_BY_REGION[region];
        } else {
            return UNISEX_NAMES_BY_REGION[region];
        }
    }

    public String[] generateName() {
        randomlyAssignGender();

        int selectedRegion = selectRegion();

        String[] selectedFirstNames = getFirstNamesByGenderAndRegion(this.gender, selectedRegion);
        String[] selectedLastNames = LAST_NAMES_BY_REGION[selectedRegion];

        // Assign random name from selected list
        String firstName = selectedFirstNames[RANDOM.nextInt(selectedFirstNames.length)];
        String lastName = selectedLastNames[RANDOM.nextInt(selectedLastNames.length)];

        return new String[]{firstName, lastName, gender};
    }
}
