package de.andrenitze.softpro.entities;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import de.andrenitze.softpro.types.AvatarType;
import de.andrenitze.softpro.types.DeliveryMediumType;
import lombok.Getter;
import lombok.Setter;

public class StoryElement {
    @Getter
    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class,
            property = "id")
    @JsonIdentityReference(alwaysAsId = true)
    private int id;

    @Getter
    private AvatarType avatar;
    @Getter
    private String[] lines;
    @Setter
    @Getter
    private boolean sent;
    private boolean read;
    private int occurrence;

    /**
     * Earliest occurrence of the objective in days (=game ticks).
     * Will not spawn before that day, even if other requirements are met.
     */
    @Getter
    private int earliestOccurrence = 1;

    /**
     * Line will be shown after the referenced objective is completed.
     */
    @Getter
    private int afterObjective;

    /**
     * Delivery medium (email | face-to-face | messenger | messenger-group)
     * In none is provided, it's "face-to-face" (avatar will show up in person).
     */
    @Getter
    private DeliveryMediumType medium = DeliveryMediumType.FACE2FACE;

}