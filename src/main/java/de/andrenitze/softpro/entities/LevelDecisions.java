package de.andrenitze.softpro.entities;

import de.andrenitze.softpro.types.Decision;

import java.util.List;

public class LevelDecisions {
    private int level;
    private List<Decision> decisions;

    public int getLevel() {
        return level;
    }

    public List<Decision> getDecisions() {
        return decisions;
    }
}
