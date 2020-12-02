package de.andrenitze.softpro.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.andrenitze.softpro.Game;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Objective {
    @JsonProperty
    private Integer id;

    @JsonProperty
    private String title;

    /**
     * Order in which the objective can occur. Two objectives can have the same value.
     * An objective with an order of "2" will not spawn before all events with the order of "1"
     * and other objectives in their missions are completed.
     */
    @JsonProperty
    private int order;

    @JsonProperty
    private int rewardFunds;

    @JsonProperty
    private int totalSteps;

    @JsonProperty
    private int completedSteps = 0;

    /**
     * Earliest occurrence of the objective in days (=game ticks).
     * Will not spawn before that day, even if requirements are met.
     */
    private int earliestOccurrence = 0;

    @JsonProperty
    private String successMessage;

    @JsonProperty
    private String failureMessage;

    @JsonProperty
    private String mission; // Five words or less

    public int getCompletedSteps() {
        return completedSteps;
    }

    public void setCompletedSteps(int completedSteps) {
        this.completedSteps = completedSteps;
    }
}