package de.andrenitze.softpro.entities;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
public class Mission {
    private String title;
    private int order;
    private int earliestOccurrence;
    private List<Objective> objectives;
    @Getter @Setter
    private boolean processed = false;

    public boolean isCompleted() {
        for (Objective objective : objectives) {
            if (!objective.isCompleted()) {
                return false;
            }
        }
        return true;
    }

}