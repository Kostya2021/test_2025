package de.andrenitze.softpro.domains.projects;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectSummary {
    private int id;
    private int earnedValue;
    private int tick;

    public ProjectSummary() {}
}
