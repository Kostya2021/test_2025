package de.andrenitze.softpro;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Project {
    private static int lastId = 0;
    private final int id;
    private final String name;
    private final int totalValue;
    private int earnedValue;
    private final boolean hasTenderProcess;
    private int tenderDeadlineInDays;
    private final int riskLevel;
    private final ArrayList<Player> involvedParties = new ArrayList<>();

    public Project(String name, int totalValue, boolean hasTenderProcess) {
        this.name = name;
        this.totalValue = totalValue;
        this.earnedValue = 0;
        this.id = lastId;
        ++lastId;
        this.riskLevel = generateRiskLevel();
        this.hasTenderProcess = hasTenderProcess;

        if (hasTenderProcess) {
            this.tenderDeadlineInDays = 14;
        } else {
            this.tenderDeadlineInDays = Integer.MAX_VALUE;
        }
    }

    private int generateRiskLevel() {
        return 1;
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
    static Project generateRandomProject() {
        return new Project(generateProjectName(), generateVolume(), false);
    }

    private static int generateVolume() {
        // Generate an integer between 10.000 and 110.000
        return 10000 + new Random().nextInt(100) * 1000;
    }

    private static String generateProjectName() {
        return "PROJECT-" + lastId;
    }

    int getTotalValue() {
        return totalValue;
    }

    String getName() {
        return name;
    }

    void decreaseTimeLeftForTender() {
        --this.tenderDeadlineInDays;
    }

    int getTenderDeadlineInDays() {
        return tenderDeadlineInDays;
    }

    public void setTenderDeadlineInDays(int tenderDeadlineInDays) {
        this.tenderDeadlineInDays = tenderDeadlineInDays;
    }
    /**
     * Several companies can be associated with the same project.
     *
     * Several companies can take part in the tender process.
     * After the tender, several companies can work on the project together.
     *
     */
    void addParty(Player player) {
        if (!involvedParties.contains(player)) {
            involvedParties.add(player);
        }
    }
    List<Player> getInvolvedPlayers() {
        return involvedParties;
    }

    int getEarnedValue() {
        return earnedValue;
    }

    void setEarnedValue(int earnedValue) {
        this.earnedValue = earnedValue;
    }

    public int getRiskLevel() {
        return riskLevel;
    }

    int getId() {
        return id;
    }

    public void addEarnedValue(int addedValue) {
        setEarnedValue(getEarnedValue()+addedValue);
    }

    public boolean hasTenderProcess() {
        return hasTenderProcess;
    }
}
