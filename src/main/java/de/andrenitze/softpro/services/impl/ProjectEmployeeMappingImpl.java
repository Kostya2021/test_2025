package de.andrenitze.softpro.services.impl;

import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.projects.Project;
import de.andrenitze.softpro.services.ProjectEmployeeMappingService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Getter
@Service
@Primary
@Slf4j
@RequiredArgsConstructor
public class ProjectEmployeeMappingImpl implements ProjectEmployeeMappingService {
    private final ConcurrentMap<Project, ArrayList<Employee>> projectEmployeesMap = new ConcurrentHashMap<>();

    public void assignEmployeeToProject(Employee employee, Project project) {
        projectEmployeesMap.putIfAbsent(project, new ArrayList<>());
        ArrayList<Employee> employees = projectEmployeesMap.get(project);
        if (!employees.contains(employee)) {
            employees.add(employee);
            projectEmployeesMap.put(project, employees);
            log.debug("{} assigned to {}", employee.getName(), project.getName());
        }
    }

    public void removeEmployeeFromProject(Employee employee, Project project) {
        ArrayList<Employee> employees = projectEmployeesMap.get(project);
        if (employees != null && employees.contains(employee)) {
            employees.remove(employee);
            projectEmployeesMap.put(project, employees);
            log.debug("{} unassigned from {}", employee.getName(), project.getName());
        }
    }

    public void removeEmployeeFromAllProjects(Employee employee) {
        projectEmployeesMap.forEach((project, employees) -> {
            if (employees.contains(employee)) {
                employees.remove(employee);
                projectEmployeesMap.put(project, employees);
            }
        });
    }

    public ArrayList<Employee> getEmployeesByProject(Project project) {
        return projectEmployeesMap.get(project);
    }

    boolean isEmployeeAssignedToAnyProject(Employee employee) {
        return projectEmployeesMap.values().stream().anyMatch(employees -> employees.contains(employee));
    }

    public void addProject(Project project, ArrayList<Object> objects) {
        projectEmployeesMap.put(project, new ArrayList<>());
    }
}