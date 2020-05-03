package de.andrenitze.softpro;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Project {
    private static int id;
    private final String name;
    private final int volumeInPersonDays;
    private int earnedValue;
    private int deadlineInDays;
    private int timeLeftForTender;
    private int riskLevel;
    private final ArrayList<Player> involvedParties = new ArrayList<>();

    public Project(String name,  int volumeInPersonDays) {
        this.name = name;
        this.volumeInPersonDays = volumeInPersonDays;
        this.earnedValue = 0;
        this.timeLeftForTender = 14;
        ++id;
    }

    /**
     * Generates a project with a random name and volume
     *
     * Projects can be used in several stages. The first stage is a "tender".
     * All players can participate in tenders.
     *
     * After a tender is won by a player, work on the project can get started.
     * Work on the project increases the earnedValue. When earnedValue has reached
     * volumeInPersonDays, the project is fully delivered.ö
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

    public int getVolumeInPersonDays() {
        return volumeInPersonDays;
    }

    public String getName() {
        return name;
    }

    public void decreaseTimeLeftForTender() {
        --this.timeLeftForTender;
    }

    public int getTimeLeftForTender() {
        return timeLeftForTender;
    }

    /**
     * Several companies can be associated with the same project.
     *
     * Several companies can take part in the tender process.
     * After the tender, several companies can work on the project together.
     *
     * @param player
     */
    public void addCompany(Player player) {
        involvedParties.add(player);
    }

    public List<Player> getInvolvedParties() {
        return involvedParties;
    }

    public int getEarnedValue() {
        return earnedValue;
    }

    public void setEarnedValue(int earnedValue) {
        this.earnedValue = earnedValue;
    }

    public int getDeadlineInDays() {
        return deadlineInDays;
    }

    public void setDeadlineInDays(int deadlineInDays) {
        this.deadlineInDays = deadlineInDays;
    }

    public int getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(int riskLevel) {
        this.riskLevel = riskLevel;
    }
}
