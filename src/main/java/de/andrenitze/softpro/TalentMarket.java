package de.andrenitze.softpro;

import java.util.ArrayList;

public class TalentMarket {
    // Talent market is a list of all employees that are currently not employed by a player
    private static final ArrayList<Employee> talents = new ArrayList<>();

    public static void addTalent(Employee employee) {
        talents.add(employee);
    }

    public static void removeTalent(Employee employee) {
        talents.remove(employee);
    }

    public static ArrayList<Employee> getTalentMarket() {
        return talents;
    }

    public static void clearTalentMarket() {
        talents.clear();
    }

    public Employee hireTalent(Player player, int talentId) {
        for (Employee employee : talents) {
            if (employee.getId() == talentId) {
                player.addEmployee(employee);
                talents.remove(employee);
                return employee;
            }
        }
        return null;
    }

    public Employee getEmployeeById(int employeeId) {
        for (Employee employee : talents) {
            if (employee.getId() == employeeId) {
                return employee;
            }
        }
        return null;
    }

    public void initialize() {
        for (int i = 0; i<15; i++) {
            Employee employee = new Employee();
            addTalent(employee);
        }
    }

    public ArrayList<Employee> getTalents() {
        return talents;
    }
}
