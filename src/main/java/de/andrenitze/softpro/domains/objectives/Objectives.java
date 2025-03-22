package de.andrenitze.softpro.domains.objectives;

import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class Objectives {
    private final List<Mission> missions;

    private static final Map<Integer, Objectives> instances = new ConcurrentHashMap<>();

    private Objectives(List<Mission> missions) {
        this.missions = missions;
    }

    public static Objectives getInstance(int level) {
        return instances.computeIfAbsent(level, k -> {
            List<Mission> loadedMissions = MissionsLoader.loadMissionsFromYamlFile("level-" + k + "-objectives.yaml");
            return new Objectives(loadedMissions);
        });
    }
}