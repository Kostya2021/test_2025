package de.andrenitze.softpro.domains.projects;

import de.andrenitze.softpro.GlobalIdManager;
import de.andrenitze.softpro.domains.projects.generators.ProjectAttributesGenerator;

import java.util.List;

import static de.andrenitze.softpro.GameServer.RANDOM;

public class ProjectBuilder {

    private static final List<ProjectType> PROJECT_TYPES = List.of(ProjectType.values());
    private static final List<RiskLevel> RISK_LEVELS = List.of(RiskLevel.values());

    private final Project project = new Project();
    private boolean tenderProcessWasExplicitlySet = false;


    public ProjectBuilder() {
        project.setEarnedValue(0);
        project.setId(GlobalIdManager.generateProjectId());
    }

    public ProjectBuilder name(String customName) {
        project.setName(customName);
        return this;
    }

    public ProjectBuilder risk(RiskLevel risk){
        project.setRisk(risk);
        return this;
    }

    public ProjectBuilder type(ProjectType type) {
        project.setType(type);
        return this;
    }

    public ProjectBuilder domain(String domain) {
        project.setDomain(domain);
        return this;
    }

    public ProjectBuilder hasTenderProcess(boolean hasTenderProcess) {
        project.setTenderProcess(hasTenderProcess);
        this.tenderProcessWasExplicitlySet = true;
        return this;
    }

    public ProjectBuilder deadline(int days) {
        project.setDeadline(days);
        return this;
    }

    private void applyDefaultValues(){

        if (project.getName() == null) {
            project.setName(ProjectAttributesGenerator.generateProjectName());
        }

        // Assign random risk level, if not already set
        if (project.getRisk() == null) {
            project.setRisk( RISK_LEVELS.get(RANDOM.nextInt(RISK_LEVELS.size())) );
        }

        // Assign random project type, but not compliance projects, if not already set
        if (project.getType() == null) {
            do {
                project.setType( PROJECT_TYPES.get(RANDOM.nextInt(PROJECT_TYPES.size())) );
            }
            while (project.getType() == ProjectType.COMPLIANCE);
        }

        if (project.getDomain() == null) {
            project.setDomain(ProjectAttributesGenerator.generateDomain(project.getType()));
        }

        // Order is important. Volume depends on risk.
        if (project.getTotalValue() == 0) {
            project.setTotalValue( ProjectAttributesGenerator.generateVolume(project.getRisk()) );
        }

        if (!tenderProcessWasExplicitlySet) {
            project.setTenderProcess(false);
        }

        if (project.getType() != ProjectType.COMPLIANCE) {
            project.setDeadline(ProjectAttributesGenerator.generateDeadline(
                    project.getRisk(),
                    project.getTotalValue()
            ));
        }
    }

    public Project build() {
        applyDefaultValues();
        return project;
    }

}
