package de.andrenitze.softpro;

import de.andrenitze.softpro.types.ProjectType;
import de.andrenitze.softpro.types.RiskLevel;

import java.util.*;

public class Project {
    private static int lastId = 1;
    private final Integer id;
    private final String name;
    private final int totalValue;
    private int earnedValue;
    private final boolean hasTenderProcess;
    private int tenderDeadlineInDays;
    private final int deadline;
    private final ArrayList<Player> involvedParties = new ArrayList<>();
    private int acquiredAt;
    private int startedAt;
    private int completedAt = 0;
    private int quality;
    private float penalty;
    private float profit;
    private static final Random RANDOM = new Random();
    private final RiskLevel risk;
    private static final List<RiskLevel> RISK_LEVELS =
            List.of(RiskLevel.values());
    private final ProjectType type;
    private static final List<ProjectType> PROJECT_TYPES =
            List.of(ProjectType.values());
    private static final List<String> PROJECT_NAME_SNIPPETS = List.of("Acceleron,SKATE,SCORM,STORM,Hercules,Curie,GAIUS,HERA,EoS,HELIOS,Pontos,Theia,Terra,Nyx,DeMeTer,Aion,HALO,MoiRai,ZEUS,AGaThe,Bigfoot,Mercury,Bender,Whistler,HUSK,Sputnik,Stratos,FAST,ImPacT,Excalibur,HEX,Daemon,Key,Score,Binary".split(","));
    private static final List<String> PROJECT_NAME_SUFFIXE = List.of("V,Active,Hub,Net,NET,X,Services,Unified,Unisono,Cloud,Intelligence,Enterprise,Center".split(","));
    private static final List<String> PROJECT_NAME_SPACERS = List.of(" ,-,".split(","));
    private static final List<String> PROJECT_DOMAINS_CONSULTING = List.of("ProcessAssessment,TechnologyEvaluation,FeasibilityStudy,SWOTAnalysis".split(","));
    private static final List<String> PROJECT_DOMAINS_DEVELOPMENT = List.of("JAVA,COBOL,C,dotNET,Python,Swift,Kotlin,JavaScript,Go,PHP,Scala,CSharp".split(","));
    private static final List<String> PROJECT_DOMAINS_INTRODUCTION = List.of("ProcessAssessment,TechnologyEvaluation,FeasibilityStudy,SWOTAnalysis".split(","));
    private static final List<String> PROJECT_DOMAINS_CUSTOMIZATION = List.of("S4/MONTANA,Dynamix,TYPOW3".split(","));
    private static final List<String> PROJECT_DOMAINS_MAINTENANCE = List.of("PlatformMigration,Refactoring,QualityEvaluation,DataMigration".split(","));

    // Move to external class (ProjectGenerator)? Goal is to have unique Project names within one game instance.
    private static final Set<String> usedProjectNames = new HashSet<>();

    private static final EnumMap<ProjectType, List<String>> projectTypeDomainMap = new EnumMap<>(ProjectType.class);
    private final String domain;

    /**
     * Generates a project with a random name and volume
     *
     * Projects can be used in several stages. The first stage is a "tender".
     * All players can participate in tenders.
     *
     * After a tender is won by a player, work on the project can get started.
     * Work on the project increases the earnedValue. When earnedValue has reached
     * totalValue, the project is fully delivered.
     */
    public Project() {
        this.name = generateProjectName();
        this.totalValue = generateVolume();
        this.earnedValue = 0;
        this.id = lastId;
        ++lastId;

        // Assign random risk level
        this.risk = RISK_LEVELS.get(RANDOM.nextInt(RISK_LEVELS.size()));

        // Assign random project type
        this.type = PROJECT_TYPES.get(RANDOM.nextInt(PROJECT_TYPES.size()));

        // +40% chance of a tender process
        this.hasTenderProcess = (Math.round(RANDOM.nextFloat()+0.4) < 1);

        // Set matching candidates for project types (e. g., "Development") and domains (e. g., "COBOL")
        projectTypeDomainMap.put(ProjectType.CONSULTING, PROJECT_DOMAINS_CONSULTING);
        projectTypeDomainMap.put(ProjectType.CUSTOMIZATION, PROJECT_DOMAINS_CUSTOMIZATION);
        projectTypeDomainMap.put(ProjectType.DEVELOPMENT, PROJECT_DOMAINS_DEVELOPMENT);
        projectTypeDomainMap.put(ProjectType.INTRODUCTION, PROJECT_DOMAINS_INTRODUCTION);
        projectTypeDomainMap.put(ProjectType.MAINTENANCE, PROJECT_DOMAINS_MAINTENANCE);

        // Based on the project type, assign a matching domain
        this.domain = generateDomain(this.type);

        if (hasTenderProcess) {
            this.tenderDeadlineInDays = 20;
        } else {
            this.tenderDeadlineInDays = Integer.MAX_VALUE;
        }

        this.deadline = Math.round(totalValue / 400f);
    }

    private static String generateDomain(ProjectType type) {
        return projectTypeDomainMap.get(type).get(new Random().nextInt(projectTypeDomainMap.get(type).size()));
    }

    private static int generateVolume() {
        return 10000 + RANDOM.nextInt(1000) * 100;
    }

    private static String generateProjectName() {
        String projectName = "";
        boolean isUniqueName = false;

        while (!isUniqueName) {
            // Pick a random name
            projectName = PROJECT_NAME_SNIPPETS.get(new Random().nextInt(PROJECT_NAME_SNIPPETS.size()));

            // Add variation (how many words, dashes, prefix, suffix etc.) by chance
            if (new Random().nextInt(100) < 50) {
                String spacer = PROJECT_NAME_SPACERS.get(new Random().nextInt(PROJECT_NAME_SPACERS.size()));
                String secondPart = PROJECT_NAME_SUFFIXE.get(new Random().nextInt(PROJECT_NAME_SUFFIXE.size()));
                projectName += spacer + secondPart;
            }
            // Make sure every name is unique
            isUniqueName = usedProjectNames.add(projectName);
        }

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

    RiskLevel getRiskLevel() {
        return risk;
    }

    int getId() {
        return id;
    }

    void addEarnedValue(int addedValue, int tick) {
        setEarnedValue(Math.max(getEarnedValue() + addedValue, 0));

        if (isCompleted()) {
            setCompletedAt(tick);
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

    public int getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(int completedAt) {
        this.completedAt = completedAt;
    }

    public ProjectType getType() {
        return type;
    }

    public String getDomain() {
        return domain;
    }

    public int getDeadline() {
        return deadline;
    }

    public int getAcquiredAt() {
        return acquiredAt;
    }

    public void setAcquiredAt(int acquiredAt) {
        this.acquiredAt = acquiredAt;
    }

    public int getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(int startedAt) {
        this.startedAt = startedAt;
    }

    public int getQuality() {
        return quality;
    }

    public void setQuality(int quality) {
        this.quality = quality;
    }

    public float getPenalty() {
        return penalty;
    }

    public void setPenalty(float penalty) {
        this.penalty = penalty;
    }

    public float getProfit() {
        return profit;
    }

    public void setProfit(float profit) {
        this.profit = profit;
    }
}
