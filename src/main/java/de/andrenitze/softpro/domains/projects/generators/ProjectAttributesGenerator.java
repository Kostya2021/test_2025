package de.andrenitze.softpro.domains.projects.generators;

import de.andrenitze.softpro.domains.projects.ProjectType;
import de.andrenitze.softpro.domains.projects.RiskLevel;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static de.andrenitze.softpro.GameServer.RANDOM;
import static de.andrenitze.softpro.services.impl.ProjectServiceImpl.BASE_PRODUCTIVITY_VALUE;

@Slf4j
public class ProjectAttributesGenerator {

    // One Full Time Equivalent (FTE) can generate this amount of "value units" per day
    // This value will be modified by factors like skill, project risk, team fit, etc.
    public static final int PROJECT_VOLUME_MIN = 10000;

    private static final List<String> PROJECT_NAME_SNIPPETS = List.of("Mercury,Venus,Earth,Mars,Jupiter,Saturn,Uranus,Neptune,Pluto,Aphrodite,Apollo,Artemis,Athena,Demeter,Dionysus,Hades,Hephaestus,Hera,Hermes,Hestia,Persephone,Poseidon,Zeus,Acceleron,SKATE,SCORM,STORM,Hercules,Curie,GAIUS,HERA,EoS,HELIOS,Pontos,Theia,Terra,Nyx,DeMeTer,Aion,HALO,MoiRai,ZEUS,AGaThe,Bigfoot,Mercury,Bender,Whistler,HUSK,Sputnik,Stratos,FAST,ImPacT,Excalibur,HEX,Daemon,Key,Score,Binary,Draco,Eclipse,Andromeda,Cosmos,Orion,Nebula,Aurora,Stellar,Phoenix,Apex,Aether,Argos,Boreas,Cyber,Electra,Fury,Galaxy,Helix,Icarus,Kronos,Luna,Meteor,Nova,Onyx,Phoenix,Raptor,Saturna,Titan,Vega,Xena,Zephyr,Zodiac,Aldebaran,Betelgeuse,Centaurus,Delphinus,Eridanus,Gemini,Hercules,Io,Juno,Kraken,Leo,Mimosa,Nebula,Oberon,Pegasus,Quasar,Rigel,Sirius,Taurus,Umbriel,Venus,Wolf,Zircon,Crypto,Quest,Hyperloop,Vulcan,Quantex,Titanus,Minerva,Heliosphere,Lazarus,Venture,ZeusX,Chronos,Matrix,Avalon,Zenith,Polaris,Ether,Legend,Vortex,Astra,Nemesis,Hypernova,Solara,Archer,Invictus,Odin,Thor,Freya,Loki,Baldur,Byte,Zero,System,Cypher,Kernel,Logic,Protocol,Nexus,Mainframe,Quantum,Bit,Octet,Hex,Cluster,Cloud,Node,Stack".split(","));
    private static final List<String> PROJECT_NAME_SUFFIXES = List.of("V,Active,Hub,Net,NET,X,Services,Unified,Unisono,Cloud,Intelligence,Enterprise,Center,Portal,Pipeline,Node,Core,Server,Client,Agent,Manager,Engine,Box,Station,Suite,Pro,Plus,Advanced,Ultimate,Alpha,Beta,Gamma,Delta,Epsilon,Zeta,Eta,Theta,Iota,Kappa,Lambda,Mu,Nu,Xi,Omicron,Pi,Rho,Sigma,Tau,Upsilon,Phi,Chi,Psi,Omega,Velocity,Harmony,Fusion,Apex,Nimbus,Nova,Orion,Quasar,Radiance,Spectrum,Infinity,Genesis,Evolve,Solstice,Cybernetics,Empire,Paragon,Cosmic,Astral,Interstellar,Revolution,Sentinel,Quantum,Centauri,Zenith,Eclipse,Hyperion,Voyager,Serenity,Innovation,Nebula,Trinity,Mirage,Ascend,Aegis,Elysium,Eon,Infinity,Horizon,.io,Crypt,Matrix,Vertex,Galactic,Empyrean,Continuum,Dimension,Realm,Vertex,Aeon,Chronicle,Vision,Odyssey,Ether,Portal,Expanse,Vanguard,Guardian,Legend,Mystic,Realm,Digital,Frontier,Architect,Virtue,Valor,Unity,Chronos,Domain,Echo,Flux,Haven,Illuminati,Journey,Keystone,Legacy,Mastery,Nexus,Oasis,Pinnacle,Refuge,Spire,Threshold,Undertow,Venture,Whisper,Xenon,Yield,Zen,Protocol,System,Bit,Stream,Array,Packet,Frame,Sync,Cache,Wire,Loop,Cipher,Source".split(","));
    private static final List<String> PROJECT_NAME_SPACERS = List.of(" ,-,".split(","));
    private static final int PROJECT_NAME_SNIPPETS_SIZE = PROJECT_NAME_SNIPPETS.size();
    private static final int PROJECT_NAME_SPACERS_SIZE = PROJECT_NAME_SPACERS.size();
    private static final int PROJECT_NAME_SUFFIXES_SIZE = PROJECT_NAME_SUFFIXES.size();

    private static final EnumMap<ProjectType, List<String>> projectTypeDomainMap = new EnumMap<>(ProjectType.class);

    // Move to external class (ProjectGenerator)? Goal is to have unique Project names within one game instance.
    private static final Set<String> usedProjectNames = new HashSet<>();

    static {setMatchingDomainForType();}

    private static void setMatchingDomainForType() {
        // Set matching candidates for project types (e.g., "Development") and domains (e.g., "COBOL")
        projectTypeDomainMap.put(ProjectType.CONSULTING, ProjectType.CONSULTING_DOMAINS);
        projectTypeDomainMap.put(ProjectType.CUSTOMIZATION, ProjectType.CUSTOMIZATION_DOMAINS);
        projectTypeDomainMap.put(ProjectType.DEVELOPMENT, ProjectType.DEVELOPMENT_DOMAINS);
        projectTypeDomainMap.put(ProjectType.INTRODUCTION, ProjectType.INTRODUCTION_DOMAINS);
        projectTypeDomainMap.put(ProjectType.MAINTENANCE, ProjectType.MAINTENANCE_DOMAINS);
    }

    public static String generateProjectName() {
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
                log.debug("Could not generate unique project name after 10 iterations. Returning name anyway.");
                break;
            }
        }
        return projectName.toString();
    }

    public static int generateVolume(RiskLevel risk) {
        // The higher the risk level the higher the project volume
        float riskMultiplier = switch (risk) {
            case LOW -> 1.0f;
            case MEDIUM -> 2.0f;
            case HIGH -> 4.0f;
            case EXTREME -> 8.0f;
        };

        int randomVolume = RANDOM.nextInt(1000) * 100;
        return (int) (PROJECT_VOLUME_MIN + riskMultiplier * randomVolume);
    }

    public static int generateDeadline(RiskLevel risk, int totalValue) {
        // The deadline of a project is independent of the players' capacity.
        // By defining a deadline, inherently, every project is suited for a specific number of people.

        float riskMultiplier = switch (risk) {
            case LOW -> 2.0f;
            case MEDIUM -> 1.0f;
            case HIGH -> 0.5f;
            case EXTREME -> 0.25f;
        };

        // Generate random deadline, loosely based on project volume
        return (int) (((float) totalValue / BASE_PRODUCTIVITY_VALUE) * riskMultiplier * RANDOM.nextFloat(0.8f, 1.9f));
    }

    public static String generateDomain(ProjectType type) {
        return projectTypeDomainMap.get(type).get(RANDOM.nextInt(projectTypeDomainMap.get(type).size()));
    }

}
