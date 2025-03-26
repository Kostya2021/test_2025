package de.andrenitze.softpro.services;

import de.andrenitze.softpro.Player;
import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.projects.Project;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

/**
 * Service für die Verwaltung von Projekten.
 */
public interface ProjectService {
    void conductWorkOnAllProjects(int tick, int level, LocalDate currentDate, ConcurrentMap<Project,
            ArrayList<Employee>> projectEmployeesMap);
    int calculateEmployeeEarnedValue(Employee employee, Project project, int tick, float onboardingFactor);
    void cancelProject(Player player, Project project, String cancelledBy, int tick, int level);
    void cancelOverdueProjects(int tick, int level);
    void setProjects(List<Object> objects);
    void addProject(Project project);

}