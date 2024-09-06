package de.andrenitze.softpro.entities;

import lombok.Getter;

import java.util.List;

@Getter
public class Mission {
    private String title;
    private int order;
    private int earliestOccurrence;
    private List<Objective> objectives;

    public boolean isCompleted() {
        for (Objective objective : objectives) {
            if (!objective.isCompleted()) {
                return false;
            }
        }
        return true;
    }
}