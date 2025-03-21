package de.andrenitze.softpro.domains.projects;

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
