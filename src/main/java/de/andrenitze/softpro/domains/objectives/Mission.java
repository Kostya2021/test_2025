package de.andrenitze.softpro.domains.objectives;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@EqualsAndHashCode
public class Mission {
    private String title;
    private int order;
    private int earliestOccurrence;
    private List<Objective> objectives;
    private boolean processed = false;

    public boolean isNotCompleted() {
        for (Objective objective : objectives) {
            if (!objective.isCompleted()) {
                return true;
            }
        }
        return false;
    }

}