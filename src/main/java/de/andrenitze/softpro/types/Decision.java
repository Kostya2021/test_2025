package de.andrenitze.softpro.types;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter @AllArgsConstructor
public class Decision {
    private int decisionId;
    private int optionId;
}