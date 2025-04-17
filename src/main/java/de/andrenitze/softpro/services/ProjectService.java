package de.andrenitze.softpro.services;

import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.players.Player;
import de.andrenitze.softpro.domains.projects.Project;

import java.time.LocalDate;
import java.util.List;

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
    List<Project> getProjects();
    void assessProjectRiskForPlayer(Project project, Player player, int tick, int level);
    Project getProjectById(int projectId);
    void conductTeamEstimation(int projectId, Player player, int tick);
    void startProject(Project project, int startedAt);
    void initializeProjectMarket(int level);
    void spawnTenders(int tick);
    void randomlySpawnLevel1Tenders(int tick, Player player);
}