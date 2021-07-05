package de.andrenitze.softpro;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Project {
    private static int lastId = 1;
    private final Integer id;
    private final String name;
    private final int totalValue;
    private int earnedValue;
    private final boolean hasTenderProcess;
    private int tenderDeadlineInDays;
    private final int riskLevel;
    private final ArrayList<Player> involvedParties = new ArrayList<>();
    private int completedTick;
    private static final List<String> PROJECT_NAME_SNIPPETS = List.of("Curie,GAIUS,HERA,EoS,HELIOS,Pontos,Theia,Terra,Nyx,DeMeTer,Aion,HALO,MoiRai,ZEUS,AGaThe,Bigfoot,Mercury,Bender,Whistler,HUSK,Sputnik,Stratos,FAST,ImPacT,Excalibur,HEX,Daemon,KeyScore,BinAry".split(","));
    private static final List<String> PROJECT_NAME_SUFFIXE = List.of("Active,Hub,Net,NET,Converse,-X,Services,ix,Unified,Unisono,Cloud,Intelligence,Enterprise,Center,Response,".split(","));
    private static final List<String> PROJECT_NAME_SPACERS = List.of(" ,-,".split(","));

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
        return new Random().nextInt(3);
    }

    /**
     * Generates a project with a random name and volume
     *
     * Projects can be used in several stages. The first stage is a "tender".
     * All players can participate in tenders.
     *
     * After a tender is won by a player, work on the project can get started.
     * Work on the project increases the earnedValue. When earnedValue has reached
     * totalValue, the project is fully delivered.
     *
     * @return Project
     */
    static Project generateRandomProject() {
        return new Project(generateProjectName(), generateVolume(), false);
    }

    private static int generateVolume() {
        return 10000 + new Random().nextInt(100) * 1000;
    }

    private static String generateProjectName() {
        // Pick some random name
        String firstPart = PROJECT_NAME_SNIPPETS.get(new Random().nextInt(PROJECT_NAME_SNIPPETS.size()));
        String projectName = firstPart;

        // Add variation (how many words, dashes, prefix, suffix etc.) by chance
        if (new Random().nextInt(100) < 50) {
            String spacer = PROJECT_NAME_SPACERS.get(new Random().nextInt(PROJECT_NAME_SPACERS.size()));
            String secondPart = PROJECT_NAME_SUFFIXE.get(new Random().nextInt(PROJECT_NAME_SUFFIXE.size()));
            projectName += spacer + secondPart;
        }

        // TODO Make sure every name is unique


        return projectName;
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

    int getRiskLevel() {
        return riskLevel;
    }

    int getId() {
        return id;
    }

    void addEarnedValue(int addedValue, int tick) {
        setEarnedValue(Math.max(getEarnedValue() + addedValue, 0));

        if (isCompleted()) {
            setCompletedTick(tick);
        }
    }

    boolean hasNoTenderProcess() {
        return !hasTenderProcess;
    }

    public boolean isCompleted() {
        return (getTotalValue()-getEarnedValue() <= 0);
    }

    public boolean playerWasInvolved(Player player) {
        return getInvolvedPlayers().contains(player);
    }

    public int getCompletedTick() {
        return completedTick;
    }

    public void setCompletedTick(int completedTick) {
        this.completedTick = completedTick;
    }
}
