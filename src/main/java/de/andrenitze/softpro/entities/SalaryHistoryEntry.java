package de.andrenitze.softpro.entities;

import lombok.Getter;

@Getter
public class SalaryHistoryEntry {
    private final int tick;
    private final int salary;

    public SalaryHistoryEntry(int tick, int salary) {
        this.tick = tick;
        this.salary = salary;
    }

}