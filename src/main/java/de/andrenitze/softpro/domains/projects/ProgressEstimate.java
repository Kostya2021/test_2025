package de.andrenitze.softpro.domains.projects;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data @AllArgsConstructor
public class ProgressEstimate implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private int tick;
    private int estimate;
}
