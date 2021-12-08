package de.andrenitze.softpro.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

public class StoryElement {
    @JsonProperty
    private Integer id;

    @JsonProperty
    private String avatar;

    /**
     * Earliest occurrence of the objective in days (=game ticks).
     * Will not spawn before that day, even if requirements are met.
     */
    @JsonProperty
    public Integer earliestOccurrence;

    /**
     * Line will be shown after the referenced objective is completed.
     */
    @JsonProperty
    private Integer afterObjective;

    /**
     * Delivery medium (email | face-to-face | messenger | messenger-group)
     * In none is provided, it's "face-to-face" (avatar will show up in person).
     */
    @JsonProperty
    private DeliveryMedium medium = DeliveryMedium.FACE2FACE;

    @JsonProperty
    private String[] lines;

    public int getEarliestOccurrence() {
        return earliestOccurrence;
    }

    public Integer getId() {
        return id;
    }
}