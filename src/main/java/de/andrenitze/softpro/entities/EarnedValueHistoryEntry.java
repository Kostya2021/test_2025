package de.andrenitze.softpro.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @AllArgsConstructor
public class EarnedValueHistoryEntry {
    private int tick;
    private double earnedValue;

    public void addValue(int addedValue) {
        this.earnedValue += addedValue;
    }
}
