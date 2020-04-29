package de.andrenitze.softpro;

import java.util.Random;

public class Project {
    private static int id;
    private final String name;
    private final int volume;

    public Project(String name,  int volume) {
        this.name = name;
        this.volume = volume;
        ++id;
    }

    /**
     * Generates a project with a random name and volume
     *
     * @return Project
     */
    public static Project generateRandomProject() {
        return new Project(generateProjectName(), generateVolume());
    }

    private static int generateVolume() {
        // Generate an integer between 10.000 and 110.000
        return 10000 + new Random().nextInt(100) * 1000;
    }

    private static String generateProjectName() {
        return "PROJECT-" + id;
    }

    public int getVolume() {
        return volume;
    }

    public String getName() {
        return name;
    }
}
