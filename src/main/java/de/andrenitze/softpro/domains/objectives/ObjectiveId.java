package de.andrenitze.softpro.domains.objectives;

import lombok.Getter;

@Getter
public enum ObjectiveId {
    OBJECTIVE_11(11),
    OBJECTIVE_12(12),
    OBJECTIVE_13(13),
    OBJECTIVE_14(14),
    OBJECTIVE_15(15),
    OBJECTIVE_16(16),
    OBJECTIVE_17(17),
    OBJECTIVE_210(210),
    OBJECTIVE_220(220),
    OBJECTIVE_222(222),
    OBJECTIVE_223(223),
    OBJECTIVE_224(224),
    OBJECTIVE_230(230),
    OBJECTIVE_231(231),
    OBJECTIVE_232(232),
    OBJECTIVE_240(240),
    OBJECTIVE_241(241),
    OBJECTIVE_31(31);

    private final int id;

    ObjectiveId(int id) {
        this.id = id;
    }

    public static ObjectiveId fromId(int id) {
        for (ObjectiveId objectiveId : values()) {
            if (objectiveId.getId() == id) {
                return objectiveId;
            }
        }
        throw new IllegalArgumentException("Unknown Objective ID: " + id);
    }
}