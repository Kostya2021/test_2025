package de.andrenitze.softpro;

import java.util.ArrayList;
import java.util.UUID;

public class Player {
    private final UUID id;
    private String name;
    private String company;
    private double funds = 100000;
    private final ArrayList<Employee> employees;

    Player() {
        this("Unknown player", "Unknown company");
    }

    Player(String name, String company) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.company = company;
        employees = new ArrayList<>();
        employees.add(new Employee());
        employees.add(new Employee());
    }

    String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public double addFunds(double additionalFunds) {
        this.funds += additionalFunds;
        return funds;
    }

    private void subtractFunds(double fundsToSubtract) {
        this.funds -= fundsToSubtract;
    }

    public double getFunds() {
        return funds;
    }

    public String getCompany() {
        return company;
    }

    ArrayList<Employee> getEmployees() {
        return employees;
    }

    void calculateAndSubtractSalaries() {
        employees.forEach(employee -> this.subtractFunds(employee.getSalary()));
    }

    Employee getEmployeeById(int id) {
        for (Employee employee : employees) {
            if (employee.getId() == id) {
                return employee;
            }
        }
        return null;
    }

    public UUID getId() {
        return id;
    }
}
