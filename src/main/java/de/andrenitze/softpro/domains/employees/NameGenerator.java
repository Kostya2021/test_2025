package de.andrenitze.softpro.domains.employees;

import static de.andrenitze.softpro.GameServer.RANDOM;

public class NameGenerator {
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
            this.gender = "male";
        } else {
            this.gender = "female";
        }
    }

    public String[] generateName() {
        // First names
        String[] maleNamesAsia = {
                "Rahul", "Siddharth", "Ramanan", "Pratik", "Prashant", "Deepak", "Mahesh",
                "Manoj", "Naveen", "Krishna", "Nishant", "Rajesh", "Rajiv", "Rakesh",
                "Ramesh", "Ravi", "Rohit", "Sachin", "Sandeep", "Sanjay", "Santosh",
                "Satish", "Shankar", "Shashi", "Shiv", "Shyam", "Siddharth", "Sudhir",
                "Sunil", "Suresh", "Umesh", "Vikram", "Vinay", "Vivek", "Yogesh"
        };

        String[] femaleNamesAsia = {
                "Rutuja", "Neelam", "Khushi", "Arusha", "Tanya", "Priyanka", "Radhika",
                "Priya", "Pooja", "Neha", "Kajal", "Jyoti", "Lata", "Sangeeta", "Meena",
                "Sunita", "Alka", "Swati", "Kiran", "Rekha", "Rupali", "Anjali", "Sushma",
                "Nandini", "Smita", "Shilpa", "Anita", "Aarti", "Kavita", "Geeta"
        };

        String[] unisexNamesAsia = {
                "Sasashy", "Arya", "Devan", "Kiran", "Ritu", "Nisha", "Rohan", "Samir",
                "Vishal", "Dev", "Anil", "Sujata", "Laxmi", "Suman", "Poonam", "Ashok",
                "Bhaskar", "Chandan", "Gagan", "Indra"
        };

        String[] maleNamesAfrica = {
                "Kwame", "Kofi", "Abdul", "Adebayo", "Chinedu", "Juma", "Kwasi", "Chukwu",
                "Sekou", "Ibrahim", "Amadou", "Tunde", "Yusuf", "Olufemi", "Kofi", "Femi",
                "Obi", "Moses", "Emeka", "Ifeanyi"
        };

        String[] femaleNamesAfrica = {
                "Fatima", "Aisha", "Zainab", "Nkechi", "Chimamanda", "Ngozi", "Yasmin",
                "Amara", "Adama", "Nala", "Chinyere", "Abena", "Sade", "Aminata", "Ayodele",
                "Bintu", "Halima", "Isioma", "Lulu", "Oluchi"
        };

        String[] unisexNamesAfrica = {
                "Chidi", "Amari", "Kamau", "Omari", "Zuri", "Eshe", "Imani", "Kato",
                "Sanyu", "Biko", "Nuru", "Ayo", "Jelani", "Malik", "Tariq", "Mosi",
                "Kito", "Nyah", "Shani", "Tari"
        };

        String[] maleNamesEurope = {
                "John", "Paul", "George", "Matthew", "Mark", "James", "Robert", "Charles",
                "Michael", "William", "Thomas", "David", "Richard", "Edward", "Henry",
                "Arthur", "Peter", "Simon", "Christopher", "Daniel"
        };

        String[] femaleNamesEurope = {
                "Olivia", "Emma", "Charlotte", "Amelia", "Ava", "Sophia", "Isabella",
                "Mia", "Evelyn", "Harper", "Luna", "Ella", "Grace", "Sophie", "Chloe",
                "Lily", "Ruby", "Hannah", "Alice", "Lucy"
        };

        String[] unisexNamesEurope = {
                "Alex", "Charlie", "Sam", "Taylor", "Jordan", "Morgan", "Casey", "Riley",
                "Jamie", "Avery", "Rowan", "Quinn", "Drew", "Elliot", "Emerson", "Finley",
                "Harper", "Peyton", "Reese", "Sage"
        };

        String[] maleNamesLatinAmerica = {
                "Carlos", "Juan", "Miguel", "Jose", "Luis", "Alejandro", "Diego", "Fernando",
                "Santiago", "Manuel", "Francisco", "Jorge", "Pedro", "Ricardo", "Andres",
                "Antonio", "Rafael", "Hector", "Gabriel", "Eduardo"
        };

        String[] femaleNamesLatinAmerica = {
                "Sofia", "Isabella", "Camila", "Valentina", "Josefina", "Gabriela", "Daniela",
                "Maria", "Carolina", "Fernanda", "Luisa", "Paula", "Adriana", "Veronica",
                "Carmen", "Victoria", "Renata", "Andrea", "Claudia", "Monica"
        };

        String[] unisexNamesLatinAmerica = {
                "Adrian", "Alex", "Andrea", "Cruz", "Emilio", "Francisco", "Jaime",
                "Julian", "Marcos", "Nico", "Paz", "Roberto", "Salvador", "Santiago",
                "Tomas", "Vicente", "Xavier", "Yago", "Zoe", "Alejandra"
        };

        String[] maleNamesNorthAmerica = {
                "Adam", "Ben", "Carl", "David", "Edward", "James", "John", "Robert",
                "Michael", "William", "Joseph", "Charles", "Thomas", "Daniel", "Christopher",
                "Matthew", "Andrew", "George", "Richard", "Henry"
        };

        String[] femaleNamesNorthAmerica = {
                "Emily", "Abigail", "Madison", "Grace", "Violet", "Sophia", "Olivia",
                "Ava", "Emma", "Isabella", "Mia", "Charlotte", "Harper", "Luna",
                "Ella", "Amelia", "Sofia", "Aria", "Scarlett", "Penelope"
        };

        String[] unisexNamesNorthAmerica = {
                "Riley", "Jordan", "Taylor", "Morgan", "Casey", "Alex", "Jamie",
                "Avery", "Rowan", "Reese", "Drew", "Harper", "Quinn", "Peyton",
                "Sage", "Bailey", "Dakota", "Cameron", "Skyler", "Hunter"
        };

        String[] maleNamesOceania = {
                "James", "William", "Lucas", "Noah", "Liam", "Oliver", "Ethan",
                "Mason", "Alexander", "Benjamin", "Henry", "Jack", "Leo", "Thomas",
                "Sebastian", "Harrison", "Levi", "Oscar", "Isaac", "Charlie"
        };

        String[] femaleNamesOceania = {
                "Ava", "Isabella", "Mia", "Evelyn", "Harper", "Amelia", "Charlotte",
                "Olivia", "Sophia", "Grace", "Emily", "Lily", "Zoe", "Hannah",
                "Lucy", "Scarlett", "Ruby", "Ella", "Isla", "Chloe"
        };

        String[] unisexNamesOceania = {
                "Charlie", "Riley", "Jordan", "Taylor", "Morgan", "Alex", "Casey",
                "Jamie", "Avery", "Rowan", "Reese", "Dakota", "Skyler", "Sage",
                "Bailey", "Cameron", "Harley", "Drew", "Quinn", "Peyton"
        };

        // Last names
        String[] lastNamesAsia = {
                "Patel", "Shah", "Gupta", "Singh", "Modi", "Rao", "Desai", "Chowdhury",
                "Reddy", "Iyer", "Pillai", "Naidu", "Bhattacharya", "Das", "Mehta",
                "Gandhi", "Sharma", "Verma", "Kapoor", "Jain"
        };

        String[] lastNamesAfrica = {
                "Okeke", "Abebe", "Hussein", "Diop", "Adebayo", "Nwosu", "Oluoch",
                "Kamara", "Mugabe", "Nkosi", "Adesina", "Omotayo", "Onyango",
                "Makinde", "Mbatha", "Thompson", "Banda", "Sibanda", "Kumalo", "Ngoma"
        };

        String[] lastNamesEurope = {
                "Smith", "Johnson", "Brown", "Taylor", "Anderson", "Miller", "Davis",
                "Wilson", "Moore", "Clark", "Thompson", "White", "Hall", "Allen",
                "Harris", "Lewis", "Walker", "Robinson", "Wood", "Wright"
        };

        String[] lastNamesLatinAmerica = {
                "Gomez", "Rodriguez", "Lopez", "Hernandez", "Martinez", "Garcia",
                "Gonzalez", "Perez", "Sanchez", "Ramirez", "Torres", "Flores",
                "Rivera", "Cruz", "Reyes", "Mendoza", "Morales", "Ortiz", "Gutierrez", "Ramos"
        };

        String[] lastNamesNorthAmerica = {
                "Miller", "Davis", "Garcia", "Martinez", "Robinson", "Anderson",
                "Wilson", "Moore", "Taylor", "Thomas", "Jackson", "White", "Harris",
                "Martin", "Thompson", "Lee", "Perez", "Walker", "Hall", "Young"
        };

        String[] lastNamesOceania = {
                "Smith", "Williams", "Brown", "Wilson", "Taylor", "Jones", "Martin",
                "Lee", "Harris", "Clark", "Robinson", "Walker", "White", "Thompson",
                "Hall", "King", "Wright", "Scott", "Green", "Baker"
        };


        // Global population distribution
        float[] regionDistribution = {0.595f, 0.172f, 0.096f, 0.084f, 0.048f, 0.005f};
        float rand = RANDOM.nextFloat();

        String[] selectedFirstNames;
        String[] selectedLastNames;

        randomlyAssignGender();

        if (rand < regionDistribution[0]) {
            // Asia
            selectedFirstNames = this.gender.equals("male") ? maleNamesAsia : this.gender.equals("female") ? femaleNamesAsia : unisexNamesAsia;
            selectedLastNames = lastNamesAsia;
        } else if (rand < regionDistribution[0] + regionDistribution[1]) {
            // Africa
            selectedFirstNames = this.gender.equals("male") ? maleNamesAfrica : this.gender.equals("female") ? femaleNamesAfrica : unisexNamesAfrica;
            selectedLastNames = lastNamesAfrica;
        } else if (rand < regionDistribution[0] + regionDistribution[1] + regionDistribution[2]) {
            // Europe
            selectedFirstNames = this.gender.equals("male") ? maleNamesEurope : this.gender.equals("female") ? femaleNamesEurope : unisexNamesEurope;
            selectedLastNames = lastNamesEurope;
        } else if (rand < regionDistribution[0] + regionDistribution[1] + regionDistribution[2] + regionDistribution[3]) {
            // Latin America
            selectedFirstNames = this.gender.equals("male") ? maleNamesLatinAmerica : this.gender.equals("female") ? femaleNamesLatinAmerica : unisexNamesLatinAmerica;
            selectedLastNames = lastNamesLatinAmerica;
        } else if (rand < regionDistribution[0] + regionDistribution[1] + regionDistribution[2] + regionDistribution[3] + regionDistribution[4]) {
            // North America
            selectedFirstNames = this.gender.equals("male") ? maleNamesNorthAmerica : this.gender.equals("female") ? femaleNamesNorthAmerica : unisexNamesNorthAmerica;
            selectedLastNames = lastNamesNorthAmerica;
        } else {
            // Oceania
            selectedFirstNames = this.gender.equals("male") ? maleNamesOceania : this.gender.equals("female") ? femaleNamesOceania : unisexNamesOceania;
            selectedLastNames = lastNamesOceania;
        }

        // Assign random name from selected list
        String firstName = selectedFirstNames[RANDOM.nextInt(selectedFirstNames.length)];
        String lastName = selectedLastNames[RANDOM.nextInt(selectedLastNames.length)];

        return new String[]{firstName, lastName, gender};
    }
}
