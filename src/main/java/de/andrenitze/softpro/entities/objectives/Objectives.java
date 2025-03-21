package de.andrenitze.softpro.entities.objectives;

import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static de.andrenitze.softpro.Main.logger;

@Getter
public class Objectives {
    private final List<Mission> missions;

    private static final Map<Integer, Objectives> instances = new ConcurrentHashMap<>();

    private Objectives(List<Mission> missions) {
        this.missions = missions;
    }

    public static Objectives getInstance(int level) {
        Objectives instance = instances.get(level);
        if (instance == null) {
            synchronized (Objectives.class) {
                instance = instances.get(level);
                if (instance == null) {
                    List<Mission> loadedMissions = MissionsLoader.loadMissionsFromYamlFile("level-" + level + "-objectives.yaml");
                    instance = new Objectives(loadedMissions);
                    instances.put(level, instance);
                    logger.debug("Creating new Objectives instance for level {} with {} missions",
                            level,
                            loadedMissions.size());
                }
            }
        }
        return instance;
    }
}