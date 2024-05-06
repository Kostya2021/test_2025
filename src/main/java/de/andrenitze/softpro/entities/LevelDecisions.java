package de.andrenitze.softpro.entities;

import de.andrenitze.softpro.types.Decision;

import java.util.List;

public record LevelDecisions(int level, List<Decision> decisions) {
}
