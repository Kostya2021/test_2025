package de.andrenitze.softpro.entities;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class AccountingEntry {
    private int day;        // Game day / tick
    private int level;      // Game level
    private int amount;     // Positive or negative
    private AccountCategory category;
    private String description;

    // Constructors, getters, and setters
    public AccountingEntry() {}

    public AccountingEntry(int day, int level, int amount, AccountCategory category, String description) {
        this.day = day;
        this.level = level;
        this.amount = amount;
        this.category = category;
        this.description = description;
    }
}