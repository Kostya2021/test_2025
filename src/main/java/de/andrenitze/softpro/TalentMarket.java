package de.andrenitze.softpro;

import de.andrenitze.softpro.types.ProjectType;

import java.util.ArrayList;
import java.util.Random;

import static de.andrenitze.softpro.Main.logger;

/**
 * The TalentMarket class is a singleton class that holds all the available talents in a running game that are
 * currently not employed by a player.
 */
public class TalentMarket {
    private static final ArrayList<Employee> talents = new ArrayList<>();
    private static final Random RANDOM = new Random();

    public EmployeeIdGenerator employeeIdGenerator;

    public TalentMarket(EmployeeIdGenerator employeeIdGenerator) {
        this.employeeIdGenerator = employeeIdGenerator;
    }

    public ArrayList<Employee> generateFirstEmployees() {
        ArrayList<Employee> employees = new ArrayList<>();

        // The first two employees have a moderate amount of XP in one random project domain
        for (int i = 0; i < 2; i++) {
            Employee employeeWithXP = new Employee(employeeIdGenerator.generateId());
            ProjectType type = ProjectType.values()[RANDOM.nextInt(ProjectType.values().length)];
            String domain = type.getDomain();
            employeeWithXP.addXp(type, domain, RANDOM.nextInt(500) + 750);
            employees.add(employeeWithXP);
        }
        return employees;
    }

    public static void addTalent(Employee employee) {
        talents.add(employee);
    }

    public static void removeTalent(Employee employee) {
        talents.remove(employee);
    }

    public static void clearTalentMarket() {
        talents.clear();
    }

    public Employee hireTalent(Player player, int talentId) {
        for (Employee employee : talents) {
            if (employee.getId() == talentId) {
                player.addEmployee(employee);
                removeTalent(employee);
                return employee;
            }
        }
        return null;
    }

    public void initialize() {
        clearTalentMarket();

        if (employeeIdGenerator == null) {
            logger.debug("EmployeeIdGenerator not set. Cannot initialize TalentMarket.");
            return;
        }

        for (int i = 0; i<15; i++) {
            Employee employee = new Employee(employeeIdGenerator.generateId());
            addTalent(employee);
        }
    }

    public ArrayList<Employee> getTalents() {
        return talents;
    }
}
