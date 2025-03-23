package de.andrenitze.softpro;

import de.andrenitze.softpro.domains.employees.Employee;
import de.andrenitze.softpro.domains.employees.EmployeeIdGenerator;
import de.andrenitze.softpro.domains.projects.ProjectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.andrenitze.softpro.services.impl.GameServerImpl.RANDOM;
import static de.andrenitze.softpro.Main.logger;

/**
 * The TalentMarket class is a singleton class that holds all the available talents in a running game instance
 * which are currently not employed by a player.
 */
public class TalentMarket {
    private final Map<Integer, Employee> talents = new HashMap<>();
    public final EmployeeIdGenerator employeeIdGenerator;

    public TalentMarket(EmployeeIdGenerator employeeIdGenerator) {
        this.employeeIdGenerator = employeeIdGenerator;
    }

    public void initialize() {
        clear();

        for (int i = 0; i < 30; i++) {
            Employee employee = new Employee(generateNewEmployeeId());
            addTalent(employee);
        }
        logger.debug("Talent market initialized with {} employees.", getTalents().size());
    }

    public synchronized List<Employee> generateFirstEmployees() {
        ArrayList<Employee> employees = new ArrayList<>();

        // The first two employees have a moderate amount of XP in one random project domain
        for (int i = 0; i < 2; i++) {
            Employee employeeWithXP = new Employee(employeeIdGenerator.generateId());
            ProjectType type = ProjectType.values()[RANDOM.nextInt(ProjectType.values().length)];
            String domain = type.getRandomDomain();
            employeeWithXP.addXp(type, domain, RANDOM.nextInt(500) + 750);
            employees.add(employeeWithXP);
        }
        return employees;
    }

    public synchronized void addTalent(Employee employee) {
        employee.setHiredAt(-1);
        talents.put(employee.getId(), employee);
    }

    public synchronized void removeTalent(Employee employee) {
        talents.remove(employee.getId());
    }

    public synchronized void clear() {
        talents.clear();
    }

    public Employee hireTalent(Player player, int talentId, int currentTick) {
        for (Employee employee : talents.values()) {
            if (employee.getId() == talentId) {
                player.addEmployee(employee, currentTick);
                removeTalent(employee);
                return employee;
            }
        }
        return null;
    }

    public synchronized int generateNewEmployeeId() {
        return employeeIdGenerator.generateId();
    }

    public List<Employee> getTalents() {
        return new ArrayList<>(talents.values());
    }
}
