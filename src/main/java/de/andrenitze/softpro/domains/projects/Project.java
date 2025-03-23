package de.andrenitze.softpro.domains.projects;

import de.andrenitze.softpro.Player;
import de.andrenitze.softpro.config.GameParameters;
import de.andrenitze.softpro.domains.employees.Employee;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

import static de.andrenitze.softpro.GameServer.RANDOM;
import static de.andrenitze.softpro.Main.logger;
import static de.andrenitze.softpro.services.impl.ProjectServiceImpl.BASE_PRODUCTIVITY_VALUE;

public class Project {
    // One Full Time Equivalent (FTE) can generate this amount of "value units" per day
    // This value will be modified by factors like skill, project risk, team fit, etc.
    public static final int PROJECT_VOLUME_MIN = 10000;
    private static int lastId = 1;
    private final Integer id;
    @Getter @Setter
    private String name;
    @Getter
    private int totalValue;
    @Getter @Setter
    private int earnedValue;
    private final List<EarnedValueHistoryEntry> earnedValueHistory = new ArrayList<>();
    private boolean tenderProcess;
    @Getter
    @Setter
    private int tenderDeadlineInDays;

    // Deadline: In how many days the project has to be finished,
    // measured from the day the project was acquired. (0 = no deadline)
    @Getter @Setter
    private int deadline;
    private final ArrayList<Player> involvedParties = new ArrayList<>();

    // acquiredAt != 0 means the project has been acquired by a player
    @Setter @Getter
    private int acquiredAt;
    @Getter @Setter
    private int startedAt;
    @Setter @Getter
    private int completedAt = 0;
    @Getter @Setter
    private int quality;
    @Getter @Setter
    private int publishedAt;
    @Getter @Setter
    private float penalty;
    @Getter @Setter
    private float profit;
    @Getter @Setter
    private RiskLevel risk;
    private static final List<RiskLevel> RISK_LEVELS = List.of(RiskLevel.values());
    @Getter
    private ProjectType type;
    private static final List<ProjectType> PROJECT_TYPES = List.of(ProjectType.values());
    private static final List<String> PROJECT_NAME_SNIPPETS = List.of("Mercury,Venus,Earth,Mars,Jupiter,Saturn,Uranus,Neptune,Pluto,Aphrodite,Apollo,Artemis,Athena,Demeter,Dionysus,Hades,Hephaestus,Hera,Hermes,Hestia,Persephone,Poseidon,Zeus,Acceleron,SKATE,SCORM,STORM,Hercules,Curie,GAIUS,HERA,EoS,HELIOS,Pontos,Theia,Terra,Nyx,DeMeTer,Aion,HALO,MoiRai,ZEUS,AGaThe,Bigfoot,Mercury,Bender,Whistler,HUSK,Sputnik,Stratos,FAST,ImPacT,Excalibur,HEX,Daemon,Key,Score,Binary,Draco,Eclipse,Andromeda,Cosmos,Orion,Nebula,Aurora,Stellar,Phoenix,Apex,Aether,Argos,Boreas,Cyber,Electra,Fury,Galaxy,Helix,Icarus,Kronos,Luna,Meteor,Nova,Onyx,Phoenix,Raptor,Saturna,Titan,Vega,Xena,Zephyr,Zodiac,Aldebaran,Betelgeuse,Centaurus,Delphinus,Eridanus,Gemini,Hercules,Io,Juno,Kraken,Leo,Mimosa,Nebula,Oberon,Pegasus,Quasar,Rigel,Sirius,Taurus,Umbriel,Venus,Wolf,Zircon,Crypto,Quest,Hyperloop,Vulcan,Quantex,Titanus,Minerva,Heliosphere,Lazarus,Venture,ZeusX,Chronos,Matrix,Avalon,Zenith,Polaris,Ether,Legend,Vortex,Astra,Nemesis,Hypernova,Solara,Archer,Invictus,Odin,Thor,Freya,Loki,Baldur,Byte,Zero,System,Cypher,Kernel,Logic,Protocol,Nexus,Mainframe,Quantum,Bit,Octet,Hex,Cluster,Cloud,Node,Stack".split(","));
    private static final List<String> PROJECT_NAME_SUFFIXES = List.of("V,Active,Hub,Net,NET,X,Services,Unified,Unisono,Cloud,Intelligence,Enterprise,Center,Portal,Pipeline,Node,Core,Server,Client,Agent,Manager,Engine,Box,Station,Suite,Pro,Plus,Advanced,Ultimate,Alpha,Beta,Gamma,Delta,Epsilon,Zeta,Eta,Theta,Iota,Kappa,Lambda,Mu,Nu,Xi,Omicron,Pi,Rho,Sigma,Tau,Upsilon,Phi,Chi,Psi,Omega,Velocity,Harmony,Fusion,Apex,Nimbus,Nova,Orion,Quasar,Radiance,Spectrum,Infinity,Genesis,Evolve,Solstice,Cybernetics,Empire,Paragon,Cosmic,Astral,Interstellar,Revolution,Sentinel,Quantum,Centauri,Zenith,Eclipse,Hyperion,Voyager,Serenity,Innovation,Nebula,Trinity,Mirage,Ascend,Aegis,Elysium,Eon,Infinity,Horizon,.io,Crypt,Matrix,Vertex,Galactic,Empyrean,Continuum,Dimension,Realm,Vertex,Aeon,Chronicle,Vision,Odyssey,Ether,Portal,Expanse,Vanguard,Guardian,Legend,Mystic,Realm,Digital,Frontier,Architect,Virtue,Valor,Unity,Chronos,Domain,Echo,Flux,Haven,Illuminati,Journey,Keystone,Legacy,Mastery,Nexus,Oasis,Pinnacle,Refuge,Spire,Threshold,Undertow,Venture,Whisper,Xenon,Yield,Zen,Protocol,System,Bit,Stream,Array,Packet,Frame,Sync,Cache,Wire,Loop,Cipher,Source".split(","));
    private static final List<String> PROJECT_NAME_SPACERS = List.of(" ,-,".split(","));
    private static final int PROJECT_NAME_SNIPPETS_SIZE = PROJECT_NAME_SNIPPETS.size();
    private static final int PROJECT_NAME_SPACERS_SIZE = PROJECT_NAME_SPACERS.size();
    private static final int PROJECT_NAME_SUFFIXES_SIZE = PROJECT_NAME_SUFFIXES.size();

    // Move to external class (ProjectGenerator)? Goal is to have unique Project names within one game instance.
    private static final Set<String> usedProjectNames = new HashSet<>();

    private static final EnumMap<ProjectType, List<String>> projectTypeDomainMap = new EnumMap<>(ProjectType.class);
    @Getter
    private String domain;
    @Getter
    private boolean hasBeenRiskAssessed = false;
    @Getter
    private final List<Problem> problems = new ArrayList<>();
    @Getter @Setter
    private List<ProgressEstimate> progressEstimates = new ArrayList<>();
    @Getter @Setter
    private int cancelledAt = 0;
    @Getter @Setter
    private String cancelledBy;

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
    }

    /**
     * Initializes a project with a random project type, domain, volume, and deadline.
     * Any attributes that are set before calling this method alter the way the project is initialized.
     */
    public Project initialize() {
        // Assign random risk level, if not already set
        if (this.risk == null) {
            this.risk = RISK_LEVELS.get(RANDOM.nextInt(RISK_LEVELS.size()));
        }

        // Assign random project type, but not compliance projects, if not already set
        if (this.type == null) {
            do {
                this.type = PROJECT_TYPES.get(RANDOM.nextInt(PROJECT_TYPES.size()));
            } while (this.type == ProjectType.COMPLIANCE);
        }

        // Order is important. Volume depends on risk.
        if (this.totalValue == 0) {
            this.totalValue = generateVolume();
        }

        this.deadline = generateDeadline();

        setMatchingDomainForType();
        // Based on the project type, assign a matching domain
        this.domain = generateDomain(this.type);

        setTenderProcess(false);

        return this;
    }

    public Project(RiskLevel riskLevel) {
        this();
        this.risk = riskLevel;
    }

    private static void setMatchingDomainForType() {
        // Set matching candidates for project types (e.g., "Development") and domains (e.g., "COBOL")
        projectTypeDomainMap.put(ProjectType.CONSULTING, ProjectType.CONSULTING_DOMAINS);
        projectTypeDomainMap.put(ProjectType.CUSTOMIZATION, ProjectType.CUSTOMIZATION_DOMAINS);
        projectTypeDomainMap.put(ProjectType.DEVELOPMENT, ProjectType.DEVELOPMENT_DOMAINS);
        projectTypeDomainMap.put(ProjectType.INTRODUCTION, ProjectType.INTRODUCTION_DOMAINS);
        projectTypeDomainMap.put(ProjectType.MAINTENANCE, ProjectType.MAINTENANCE_DOMAINS);
    }

    public Project(ProjectType type, String domain, RiskLevel risk, boolean hasTenderProcess) {
        // Warning: The problem with this constructor is that it circumvents
        // project creation logic (e.g., volume correlates with risk).
        this();
        this.type = type;
        this.domain = domain;
        this.risk = risk;
        this.totalValue = generateVolume();
        this.tenderProcess = hasTenderProcess;
    }

    public void setTenderProcess(boolean hasTenderProcess) {
        this.tenderProcess = hasTenderProcess;

        if (hasTenderProcess) {
            this.tenderDeadlineInDays = 20;
        } else {
            this.tenderDeadlineInDays = -1;
        }
    }

    private int generateDeadline() {
        // The deadline of a project is independent of the players' capacity.
        // By defining a deadline, inherently, every project is suited for a specific number of people.

        float riskMultiplier = switch (getRiskLevel()) {
            case LOW -> 2.0f;
            case MEDIUM -> 1.0f;
            case HIGH -> 0.5f;
            case EXTREME -> 0.25f;
        };

        // Generate random deadline, loosely based on project volume
        return (int) (((float) getTotalValue() / BASE_PRODUCTIVITY_VALUE) * riskMultiplier * RANDOM.nextFloat(0.8f, 1.9f));
    }

    private static String generateDomain(ProjectType type) {
        return projectTypeDomainMap.get(type).get(RANDOM.nextInt(projectTypeDomainMap.get(type).size()));
    }

    private int generateVolume() {
        // The higher the risk level the higher the project volume
        float riskMultiplier = switch (getRiskLevel()) {
            case LOW -> 1.0f;
            case MEDIUM -> 2.0f;
            case HIGH -> 4.0f;
            case EXTREME -> 8.0f;
        };

        int randomVolume = RANDOM.nextInt(1000) * 100;
        return (int) (PROJECT_VOLUME_MIN + riskMultiplier * randomVolume);
    }

    private static String generateProjectName() {
        StringBuilder projectName = new StringBuilder();
        boolean isUniqueName = false;
        int i = 0;

        while (!isUniqueName) {
            // Pick a random name
            projectName.append(PROJECT_NAME_SNIPPETS.get(RANDOM.nextInt(PROJECT_NAME_SNIPPETS_SIZE)));

            // Add 50% chance for suffixes
            if (RANDOM.nextInt(100) < 50) {
                String spacer = PROJECT_NAME_SPACERS.get(RANDOM.nextInt(PROJECT_NAME_SPACERS_SIZE));
                String secondPart = PROJECT_NAME_SUFFIXES.get(RANDOM.nextInt(PROJECT_NAME_SUFFIXES_SIZE));

                projectName.append(spacer);
                projectName.append(secondPart);
            }

            // Make sure every name is unique
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

    public void decreaseTimeLeftForTender() {
        --this.tenderDeadlineInDays;
    }

    /**
     * Several companies can be associated with the same project.
     * Several companies can take part in the tender process.
     * After the tender, several companies can work on the project together.
     *
     */
    public void addParty(Player player) {
        if (!involvedParties.contains(player)) {
            involvedParties.add(player);
        }
    }
    public List<Player> getInvolvedPlayers() {
        return involvedParties;
    }

    public RiskLevel getRiskLevel() {
        return risk;
    }

    public int getId() {
        return id;
    }

    public void addEarnedValue(int addedValue, int tick) {
        setEarnedValue(Math.max(getEarnedValue() + addedValue, 0));

        if (isCompleted()) {
            setCompletedAt(tick);
        }

        // Add earned value to the correct tick in the history
        // This is required because earnedValue can be added for multiple employees in one tick
        earnedValueHistory.stream()
                .filter(entry -> entry.getTick() == tick)
                .findFirst()
                .ifPresentOrElse(
                        entry -> entry.addValue(addedValue),
                        () -> earnedValueHistory.add(new EarnedValueHistoryEntry(tick, getEarnedValue()))
                );
    }

    public boolean hasNoTenderProcess() {
        return !tenderProcess;
    }

    public boolean isCompleted() {
        return (getTotalValue()-getEarnedValue() <= 0);
    }

    public boolean playerWasInvolved(Player player) {
        return getInvolvedPlayers().contains(player);
    }

    public boolean hasOnboardingEmployees(List<Employee> employees) {
        if (this.getType() == ProjectType.COMPLIANCE) {
            return false;
        }

        // After "safe period": Does any of the employees need on-boarding?
        int safePeriodInDays = (int) (GameParameters.SAFE_PERIOD_PERCENT * getScheduledDuration())
                + GameParameters.ASSIGNMENT_TIME_IN_DAYS;
        for (Employee employee : employees) {
            // Employee has no experience in this project and needs to be trained
            if (employee.getExperienceInDaysByProject(this) <= safePeriodInDays) {
                return true;
            }
        }

        return false;
    }

    public boolean isRampingUp(int currentTick) {
        // No extra on-boarding effort is assigned at the beginning of the project for the beginning of a project
        // (time to allocate staff to project, also general ramp-up, s. Rule #2)
        // Safe period (10%). No training required.
        return currentTick <= (getScheduledDuration() * GameParameters.SAFE_PERIOD_PERCENT
                + getAcquiredAt()
                + GameParameters.ASSIGNMENT_TIME_IN_DAYS);
    }

    public int getScheduledDuration() {
        return getDeadline() - getAcquiredAt();
    }

    public boolean hasBeenStarted() {
        return getStartedAt() > 0;
    }

    public void addProblem(Problem problem) {
        problems.add(problem);
    }

    public List<Problem> getUnsolvedProblems() {
        return problems.stream().filter(problem -> !problem.isSolved()).toList();
    }

    public void addProgressEstimate(int tick, int estimate) {
        progressEstimates.add(new ProgressEstimate(tick, estimate));
    }

    public void estimateProgress(int currentTick) {
        // Estimate progress (0-100) based on the earned value
        int estimate = (int) (100.0 * getEarnedValue() / getTotalValue());

        // Project has not been started yet
        if (!hasBeenStarted()) {
            return;
        }

        // Add variance of up to 70% based on risk level (e.g., the more risk the more variance)
        float riskMultiplier = switch (getRiskLevel()) {
            case LOW -> 0.3f;
            case MEDIUM -> 0.5f;
            case HIGH -> 0.7f;
            case EXTREME -> 1.0f;
        };


        int adjustedEstimate = (int) (riskMultiplier * estimate);
        estimate += adjustedEstimate > 0 ? RANDOM.nextInt(adjustedEstimate) : 0;
        addProgressEstimate(currentTick, Math.min(90, estimate)); // 90% is the maximum progress estimate
    }

    public void removeParty(Player player) {
        this.involvedParties.remove(player);
    }
}
