package de.andrenitze.softpro.Server;

import java.util.ArrayList;

public class Player {
    private final String name;

    private double funds;
    private final ArrayList<Employee> employees;

    public Player() {
        name = "Unknown player";
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

    public void subtractFunds(double subtractedFunds) {
        this.funds -= subtractedFunds;
    }

    public double getFunds() {
        return funds;
    }
}
