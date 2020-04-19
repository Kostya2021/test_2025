package de.andrenitze.softpro;

import java.util.ArrayList;

public class Player {
    private String name;
    private String company;

    private double funds;
    private final ArrayList<Employee> employees;

    Player() {
        name = "Unknown player";
        company = "Unknown company";
        funds = 100000;
        employees = new ArrayList<>();
        employees.add(new Employee());
    }

    public String getName() {
        return name;
    }

    public void addFunds(double additionalFunds) {
        this.funds += additionalFunds;
    }

    private void subtractFunds(double fundsToSubtract) {
        this.funds -= fundsToSubtract;
    }

    public double getFunds() {
        return funds;
    }

    void calculateAndSubtractSalaries() {
        this.subtractFunds(10000);
    }
}
