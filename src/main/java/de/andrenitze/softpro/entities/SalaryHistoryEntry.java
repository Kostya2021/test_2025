package de.andrenitze.softpro.entities;

import lombok.Getter;

@Getter
public class SalaryHistoryEntry {
    private int tick;
    private int salary;

    public SalaryHistoryEntry(int tick, int salary) {
        this.tick = tick;
        this.salary = salary;
    }

}