package de.andrenitze.softpro.entities;

import lombok.Getter;
import lombok.Setter;

@Getter
public class Objective {
    private Integer id;
    private String title;
    /**
     * Order in which the objective can occur. Two objectives can have the same value.
     * An objective with an order of "2" will not spawn before all events with the order of "1"
     * and other objectives in their missions are completed.
     */
    private int order;
    private int rewardFunds;
    private int totalSteps = 1;
    @Setter
    private int completedSteps = 0;
    @Setter
    private String mission; // Only for the frontend
    /**
     * Earliest occurrence of the objective in days (=game ticks).
     * Will not spawn before that day, even if requirements are met.
     */
    private String successMessage;
    private String failureMessage;

    public boolean isCompleted() {
        return ((completedSteps == totalSteps));
    }

    public void markAsCompleted() {
        this.completedSteps = totalSteps;
    }

    public void setCompleted(boolean b) {
        if (b) {
            this.completedSteps = totalSteps;
        } else {
            this.completedSteps = 0;
        }
    }
}