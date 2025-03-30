package de.andrenitze.softpro.domains.story;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import lombok.Getter;
import lombok.Setter;

@Getter
public class StoryElement {
    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class,
            property = "id")
    @JsonIdentityReference(alwaysAsId = true)
    private int id;

    private AvatarType avatar;
    private String[] lines;
    @Setter
    private boolean sent;
    private boolean read;
    private int occurrence;

    /**
     * Earliest occurrence of the objective in days (=game ticks).
     * Will not spawn before that day, even if other requirements are met.
     */
    private int earliestOccurrence = 1;

    /**
     * Line will be shown after the referenced objective is completed.
     */
    private int afterObjective;

    /**
     * Delivery medium (email | face-to-face | messenger | messenger-group)
     * In none is provided, it's "face-to-face" (avatar will show up in person).
     */
    private DeliveryMediumType medium = DeliveryMediumType.FACE2FACE;

}