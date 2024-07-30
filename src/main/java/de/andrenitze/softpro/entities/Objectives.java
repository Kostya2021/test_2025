package de.andrenitze.softpro.entities;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static de.andrenitze.softpro.Main.logger;

public class Objectives {
    private final ArrayList<Objective> objectives;

    private static final Map<Integer, Objectives> instances = new ConcurrentHashMap<>();

    private Objectives(ArrayList<Objective> objectives) {
        this.objectives = objectives;
    }

    public static Objectives getInstance(int level) {
        logger.debug("Trying to get Objectives instance for level {}", level);
        Objectives instance = instances.get(level);
        if (instance == null) {
            synchronized (Objectives.class) {
                instance = instances.get(level);
                if (instance == null) {
                    ArrayList<Objective> loadedObjectives = ObjectivesLoader.loadObjectivesFromYamlFile("level-" + level + "-objectives.yaml");
                    instance = new Objectives(loadedObjectives);
                    instances.put(level, instance);
                    logger.debug("Creating new Objectives instance for level {} with {} objectives",
                            level,
                            loadedObjectives.size());
                }
            }
        }
        return instance;
    }

    public ArrayList<Objective> getObjectives() {
        return objectives;
    }
}
