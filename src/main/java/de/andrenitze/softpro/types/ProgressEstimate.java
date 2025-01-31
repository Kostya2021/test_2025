package de.andrenitze.softpro.types;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data @AllArgsConstructor
public class ProgressEstimate {
    private int tick;
    private int estimate;
}
