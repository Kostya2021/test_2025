package de.andrenitze.softpro.domains.decisions;

import java.util.List;

public record LevelDecisions(int level, List<Decision> decisions) {
}
