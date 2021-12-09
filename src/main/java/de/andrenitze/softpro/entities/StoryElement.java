package de.andrenitze.softpro.entities;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import de.andrenitze.softpro.types.AvatarType;
import de.andrenitze.softpro.types.DeliveryMediumType;

public class StoryElement {
    @JsonProperty
    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class,
            property = "id")
    @JsonIdentityReference(alwaysAsId = true)
    private int id;

    @JsonProperty
    private AvatarType avatar;

    /**
     * Earliest occurrence of the objective in days (=game ticks).
     * Will not spawn before that day, even if requirements are met.
     */
    @JsonProperty
    public int earliestOccurrence = 1;

    /**
     * Line will be shown after the referenced objective is completed.
     */
    @JsonProperty
    private int afterObjective;

    /**
     * Delivery medium (email | face-to-face | messenger | messenger-group)
     * In none is provided, it's "face-to-face" (avatar will show up in person).
     */
    @JsonProperty
    private DeliveryMediumType medium = DeliveryMediumType.FACE2FACE;

    @JsonProperty
    private String[] lines;

    private boolean sent;

    public int getEarliestOccurrence() {
        return earliestOccurrence;
    }

    public int getId() {
        return id;
    }

    public String[] getLines() {
        return lines;
    }

    public int getAfterObjective() {
        return afterObjective;
    }

    public DeliveryMediumType getMedium() {
        return medium;
    }

    public AvatarType getAvatar() {
        return avatar;
    }

    public boolean isSent() {
        return sent;
    }

    public void setSent(boolean sent) {
        this.sent = sent;
    }
}