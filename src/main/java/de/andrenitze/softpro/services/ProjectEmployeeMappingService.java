package de.andrenitze.softpro.services;

import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.projects.Project;

import java.util.ArrayList;

public interface ProjectEmployeeMappingService {
    void assignEmployeeToProject(Employee employee, Project project);
    void removeEmployeeFromProject(Employee employee, Project project);
    void removeEmployeeFromAllProjects(Employee employee);
    ArrayList<Employee> getEmployeesByProject(Project project);
    void addProject(Project project, ArrayList<Object> objects);
}
