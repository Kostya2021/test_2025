package de.andrenitze.softpro.services;

import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.players.Player;
import de.andrenitze.softpro.domains.projects.Project;
import de.andrenitze.softpro.domains.projects.ProjectSummary;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service für die Verwaltung von Projekten.
 */
public interface ProjectService {
    List<Project>  conductWorkOnAllProjects(int tick, int level, LocalDate currentDate);
    int calculateEmployeeEarnedValue(Employee employee, Project project, int tick, float onboardingFactor);
    void cancelProject(Player player, Project project, String cancelledBy, int tick, int level);
    void cancelOverdueProjects(int tick, int level);
    void setProjects(List<Project> projects);
    void addProject(Project project);
    void assignProjectToPlayer(Player player, Project project, int tick);
    void assessProjectRiskForPlayer(Project project, Player player, int tick, int level);
    List<Project> getProjects();
    Project getProjectById(int projectId);
    List<Project> getProjectsByPlayer(Player player);
    void conductTeamEstimation(int projectId, Player player, int tick);
    void startProject(Project project, int startedAt);
    void initialize(int level);
    void spawnTenders(int tick);
    void randomlySpawnLevel1Tenders(int tick, Player player);
    Optional<ProjectSummary> getProjectSummaryIfProgressChanged(Project project, int tick);
    void evaluateTenderProcesses(int tick);
    void spawnComplianceProjects(int tick);
    void startStaleProjects(int tick);
    void createProblemsInProjects(int tick, int level);
    void loadProblems(int level);
    List<Project> getStaleTenders(int tick);
    Project getPreviousState(int id);
    void updatePreviousState(Project project);
}