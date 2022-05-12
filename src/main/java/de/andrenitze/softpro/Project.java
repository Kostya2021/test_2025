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
    private int deadline;
    private final ArrayList<Player> involvedParties = new ArrayList<>();
    private int completedAt;
    private static final Random RANDOM = new Random();
    private RiskLevel risk;
    private static final List<RiskLevel> RISK_LEVELS =
            List.of(RiskLevel.values());
    private ProjectType type;
    private static final List<ProjectType> PROJECT_TYPES =
            List.of(ProjectType.values());
    private static final List<String> PROJECT_NAME_SNIPPETS = List.of("Acceleron,SKATE,SCORM,STORM,Hercules,Curie,GAIUS,HERA,EoS,HELIOS,Pontos,Theia,Terra,Nyx,DeMeTer,Aion,HALO,MoiRai,ZEUS,AGaThe,Bigfoot,Mercury,Bender,Whistler,HUSK,Sputnik,Stratos,FAST,ImPacT,Excalibur,HEX,Daemon,Key,Score,Binary".split(","));
    private static final List<String> PROJECT_NAME_SUFFIXE = List.of("V,Active,Hub,Net,NET,X,Services,Unified,Unisono,Cloud,Intelligence,Enterprise,Center".split(","));
    private static final List<String> PROJECT_NAME_SPACERS = List.of(" ,-,".split(","));
    private static final List<String> PROJECT_DOMAINS_CONSULTING = List.of("ProcessAssessment,TechnologyEvaluation,FeasibilityStudy,SWOTAnalysis".split(","));
    private static final List<String> PROJECT_DOMAINS_DEVELOPMENT = List.of("JAVA,COBOL,C,dotNET,Python,Swift,Kotlin,JavaScript,Go,PHP,Scala,CSharp".split(","));
    private static final List<String> PROJECT_DOMAINS_INTRODUCTION = List.of("ProcessAssessment,TechnologyEvaluation,FeasibilityStudy,SWOTAnalysis".split(","));
    private static final List<String> PROJECT_DOMAINS_CUSTOMIZATION = List.of("S4/MONTANA,Dynamix,TYPOW3".split(","));

    // Move to external class (ProjectGenerator)? Goal is to have unique Project names within one game instance.
    private static final Set<String> usedProjectNames = new HashSet<>();

    private static final EnumMap<ProjectType, List<String>> projectTypeDomainMap = new EnumMap<>(ProjectType.class);
    private final String domain;
    private int acquiredAt;

    public Project(String name, int totalValue, boolean hasTenderProcess) {
        this.name = name;
        this.totalValue = totalValue;
        this.earnedValue = 0;
        this.id = lastId;
        ++lastId;
        this.risk = RISK_LEVELS.get(RANDOM.nextInt(RISK_LEVELS.size()));
        this.type = PROJECT_TYPES.get(RANDOM.nextInt(PROJECT_TYPES.size()));
        this.hasTenderProcess = hasTenderProcess;

        projectTypeDomainMap.put(ProjectType.CONSULTING, PROJECT_DOMAINS_CONSULTING);
        projectTypeDomainMap.put(ProjectType.CUSTOMIZATION, PROJECT_DOMAINS_CUSTOMIZATION);
        projectTypeDomainMap.put(ProjectType.DEVELOPMENT, PROJECT_DOMAINS_DEVELOPMENT);
        projectTypeDomainMap.put(ProjectType.INTRODUCTION, PROJECT_DOMAINS_INTRODUCTION);
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
        boolean hasTender = (Math.round(RANDOM.nextFloat()+0.4) < 1);
        return new Project(generateProjectName(), generateVolume(), hasTender);
    }

    private static int generateVolume() {
        return 10000 + RANDOM.nextInt(1000) * 100;
    }

    private static String generateProjectName() {
        String projectName = "";
        boolean isUniqueName = false;

        while (!isUniqueName) {
            // Pick some random name
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
}
