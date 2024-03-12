package de.andrenitze.softpro;

import de.andrenitze.softpro.types.ProjectType;
import de.andrenitze.softpro.types.RiskLevel;

import java.util.*;

import static de.andrenitze.softpro.Main.logger;

public class Project {
    // One Full Time Equivalent (FTE) can generate this amount of "value units" per day
    // This value will be modified by factors like skill, project risk, team fit, etc.
    public static final int PROJECT_VOLUME_MIN = 10000;
    private static int lastId = 1;
    private final Integer id;
    private final String name;
    private final int totalValue;
    private int earnedValue;
    private final boolean hasTenderProcess;
    private int tenderDeadlineInDays;

    // Deadline: In how many days the project has to be finished, measured from the day the project was acquired.
    private final int deadline;
    private final ArrayList<Player> involvedParties = new ArrayList<>();
    private int acquiredAt;
    private int startedAt;
    private int completedAt = 0;
    private int quality;
    private int publishedAt;
    private float penalty;
    private float profit;
    private static final Random random = new Random();
    private final RiskLevel risk;
    private static final List<RiskLevel> RISK_LEVELS = List.of(RiskLevel.values());
    private final ProjectType type;
    private static final List<ProjectType> PROJECT_TYPES = List.of(ProjectType.values());
    private static final List<String> PROJECT_NAME_SNIPPETS = List.of("Mercury,Venus,Earth,Mars,Jupiter,Saturn,Uranus,Neptune,Pluto,Aphrodite,Apollo,Artemis,Athena,Demeter,Dionysus,Hades,Hephaestus,Hera,Hermes,Hestia,Persephone,Poseidon,Zeus,Acceleron,SKATE,SCORM,STORM,Hercules,Curie,GAIUS,HERA,EoS,HELIOS,Pontos,Theia,Terra,Nyx,DeMeTer,Aion,HALO,MoiRai,ZEUS,AGaThe,Bigfoot,Mercury,Bender,Whistler,HUSK,Sputnik,Stratos,FAST,ImPacT,Excalibur,HEX,Daemon,Key,Score,Binary,Draco,Eclipse,Andromeda,Cosmos,Orion,Nebula,Aurora,Stellar,Phoenix,Apex,Aether,Argos,Boreas,Cyber,Electra,Fury,Galaxy,Helix,Icarus,Kronos,Luna,Meteor,Nova,Onyx,Phoenix,Raptor,Saturna,Titan,Vega,Xena,Zephyr,Zodiac,Aldebaran,Betelgeuse,Centaurus,Delphinus,Eridanus,Gemini,Hercules,Io,Juno,Kraken,Leo,Mimosa,Nebula,Oberon,Pegasus,Quasar,Rigel,Sirius,Taurus,Umbriel,Venus,Wolf,Zircon,Crypto,Quest,Hyperloop,Vulcan,Quantex,Titanus,Minerva,Heliosphere,Lazarus,Venture,ZeusX,Chronos,Matrix,Avalon,Zenith,Polaris,Ether,Legend,Vortex,Astra,Nemesis,Hypernova,Solara,Archer,Invictus,Odin,Thor,Freya,Loki,Baldur".split(","));
    private static final List<String> PROJECT_NAME_SUFFIXES = List.of("V,Active,Hub,Net,NET,X,Services,Unified,Unisono,Cloud,Intelligence,Enterprise,Center,Portal,Pipeline,Node,Core,Server,Client,Agent,Manager,Engine,Box,Station,Suite,Pro,Plus,Advanced,Ultimate,Alpha,Beta,Gamma,Delta,Epsilon,Zeta,Eta,Theta,Iota,Kappa,Lambda,Mu,Nu,Xi,Omicron,Pi,Rho,Sigma,Tau,Upsilon,Phi,Chi,Psi,Omega,Velocity,Harmony,Fusion,Apex,Nimbus,Nova,Orion,Quasar,Radiance,Spectrum,Infinity,Genesis,Evolve,Solstice,Cybernetics,Empire,Paragon,Cosmic,Astral,Interstellar,Revolution,Sentinel,Quantum,Centauri,Zenith,Eclipse,Hyperion,Voyager,Serenity,Innovation,Nebula,Trinity,Mirage,Ascend,Aegis,Elysium,Eon,Infinity,Horizon,.io,Crypt,Matrix,Vertex,Galactic,Empyrean,Continuum,Dimension,Realm,Vertex,Aeon,Chronicle,Vision,Odyssey,Ether,Portal,Expanse,Vanguard,Guardian,Legend,Mystic,Realm,Digital,Frontier,Architect,Virtue,Valor,Unity,Chronos,Domain,Echo,Flux,Haven,Illuminati,Journey,Keystone,Legacy,Mastery,Nexus,Oasis,Pinnacle,Refuge,Spire,Threshold,Undertow,Venture,Whisper,Xenon,Yield,Zen".split(","));
    private static final List<String> PROJECT_NAME_SPACERS = List.of(" ,-,".split(","));
    private static final int projectNameSnippetsSize = PROJECT_NAME_SNIPPETS.size();
    private static final int projectNameSpacersSize = PROJECT_NAME_SPACERS.size();
    private static final int projectNameSuffixesSize = PROJECT_NAME_SUFFIXES.size();

    // Move to external class (ProjectGenerator)? Goal is to have unique Project names within one game instance.
    private static final Set<String> usedProjectNames = new HashSet<>();

    private static final EnumMap<ProjectType, List<String>> projectTypeDomainMap = new EnumMap<>(ProjectType.class);
    private final String domain;

    // Description text is generated in the frontend
    private final String description = "";
    private boolean hasBeenRiskAssessed = false;

    /**
     * Generates a project with a random name and volume
     * Projects can be used in several stages. The first stage is a "tender".
     * All players can participate in tenders.
     * After a tender is won by a player, work on the project can get started.
     * Work on the project increases the earnedValue. When earnedValue has reached
     * totalValue, the project is fully delivered or "completed".
     */
    public Project() {
        this.name = generateProjectName();
        this.earnedValue = 0;
        this.id = lastId;
        ++lastId;

        // Assign random risk level
        this.risk = RISK_LEVELS.get(random.nextInt(RISK_LEVELS.size()));

        // Assign random project type
        this.type = PROJECT_TYPES.get(random.nextInt(PROJECT_TYPES.size()));

        // +40% chance of a tender process
        this.hasTenderProcess = (Math.round(random.nextFloat()+0.4) < 1);

        // Order is important. Volume depends on risk.
        this.totalValue = generateVolume();

        this.deadline = generateDeadline();

        // Set matching candidates for project types (e.g., "Development") and domains (e.g., "COBOL")
        projectTypeDomainMap.put(ProjectType.CONSULTING, ProjectType.CONSULTING_DOMAINS);
        projectTypeDomainMap.put(ProjectType.CUSTOMIZATION, ProjectType.CUSTOMIZATION_DOMAINS);
        projectTypeDomainMap.put(ProjectType.DEVELOPMENT, ProjectType.DEVELOPMENT_DOMAINS);
        projectTypeDomainMap.put(ProjectType.INTRODUCTION, ProjectType.INTRODUCTION_DOMAINS);
        projectTypeDomainMap.put(ProjectType.MAINTENANCE, ProjectType.MAINTENANCE_DOMAINS);

        // Based on the project type, assign a matching domain
        this.domain = generateDomain(this.type);

        if (hasTenderProcess) {
            this.tenderDeadlineInDays = 20;
        } else {
            this.tenderDeadlineInDays = Integer.MAX_VALUE;
        }
    }

    private int generateDeadline() {
        // The deadline of a project is independent of the players' capacity.
        // By defining a deadline, inherently, every project is suited for a specific number of people.

        float riskMultiplier = switch (getRiskLevel()) {
            case low -> 2.0f;
            case medium -> 1.0f;
            case high -> 0.5f;
            case extreme -> 0.25f;
        };

        // Generate random deadline, loosely based on project volume
        return (int) (getTotalValue() / Game.BASE_PRODUCTIVITY_VALUE * riskMultiplier * random.nextFloat(0.8f, 1.9f));
    }

    private static String generateDomain(ProjectType type) {
        return projectTypeDomainMap.get(type).get(new Random().nextInt(projectTypeDomainMap.get(type).size()));
    }

    private int generateVolume() {
        // The higher the risk level the higher the project volume
        float riskMultiplier = switch (getRiskLevel()) {
            case low -> 1.0f;
            case medium -> 2.0f;
            case high -> 4.0f;
            case extreme -> 8.0f;
        };

        int randomVolume = random.nextInt(1000) * 100;
        return (int) (PROJECT_VOLUME_MIN + riskMultiplier * randomVolume);
    }

    private static String generateProjectName() {
        StringBuilder projectName = new StringBuilder();
        boolean isUniqueName = false;
        int i = 0;

        while (!isUniqueName) {
            // Pick a random name
            projectName.append(PROJECT_NAME_SNIPPETS.get(random.nextInt(projectNameSnippetsSize)));
            //projectName = PROJECT_NAME_SNIPPETS.get(random.nextInt(projectNameSnippetsSize));

            // Add 50% chance for suffixes
            if (random.nextInt(100) < 50) {
                String spacer = PROJECT_NAME_SPACERS.get(random.nextInt(projectNameSpacersSize));
                String secondPart = PROJECT_NAME_SUFFIXES.get(random.nextInt(projectNameSuffixesSize));

                projectName.append(spacer);
                projectName.append(secondPart);
            }

            // Make sure, every name is unique
            isUniqueName = usedProjectNames.add(projectName.toString());
            i++;

            // After a lot of iterations, give up and return the name anyway
            if (i > 10) {
                logger.debug("Could not generate unique project name after 10 iterations. Returning name anyway.");
                break;
            }
        }
        return projectName.toString();
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

    public void setStartedAt(int startedAt) {
        this.startedAt = startedAt;
    }

    public void setQuality(int quality) {
        this.quality = quality;
    }

    public void setPenalty(float penalty) {
        this.penalty = penalty;
    }

    public void setProfit(float profit) {
        this.profit = profit;
    }

    public boolean hasOnboardingEmployees(ArrayList<Employee> employees) {
        // After "safe period": Does any of the employees need on-boarding?
        int safePeriodInDays = (int) (Params.SAFE_PERIOD_PERCENT * getScheduledDuration())
                + Params.ASSIGNMENT_TIME_IN_DAYS;
        for (Employee employee : employees) {
            // Employee has no experience in this project and needs to be trained
            if (employee.getExperienceInDaysByProject(this) <= safePeriodInDays) {
                return true;
            }
        }

        return false;
    }

    protected boolean isRampingUp(int currentTick) {
        // No extra on-boarding effort is assigned at the beginning of the project for the beginning of a project
        // (time to allocate staff to project, also general ramp-up, s. Rule #2)
        // Safe period (10%). No training required.
        return currentTick <= (getScheduledDuration() * Params.SAFE_PERIOD_PERCENT
                + getAcquiredAt()
                + Params.ASSIGNMENT_TIME_IN_DAYS);
    }

    public int getScheduledDuration() {
        return getDeadline() - getAcquiredAt();
    }

    public void setPublishedAt(int currentTick) {
        this.publishedAt = currentTick;
    }

    public int getPublishedAt() {
        return this.publishedAt;
    }

    public boolean isOverdue() {
        return getCompletedAt() > getDeadline();
    }

    public boolean hasBeenRiskAssessed() {
        return hasBeenRiskAssessed;
    }
}
