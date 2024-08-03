package de.andrenitze.softpro.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

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
    private int totalSteps = 1;

    @JsonProperty
    private int completedSteps = 0;

    /**
     * Earliest occurrence of the objective in days (=game ticks).
     * Will not spawn before that day, even if requirements are met.
     */
    @JsonProperty
    public int earliestOccurrence;

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

    public boolean isCompleted() {
        return ((completedSteps == totalSteps));
    }

    @JsonIgnore
    public int getEarliestOccurrence() {
        return earliestOccurrence;
    }

    public Integer getId() {
        return id;
    }

    public void addCompletedStep() {
        this.completedSteps++;
    }

    public void markAsCompleted() {
        this.completedSteps = totalSteps;
    }

    public String getTitle() {
        return title;
    }

    public int getOrder() {
        return order;
    }

    public int getRewardFunds() {
        return rewardFunds;
    }

    public String getSuccessMessage() {
        return successMessage;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public String getMission() {
        return mission;
    }

    public Object getTotalSteps() {
        return totalSteps;
    }
}