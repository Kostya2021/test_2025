package de.andrenitze.softpro.entities;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static de.andrenitze.softpro.Main.logger;

public class Objectives {
    private ArrayList<Objective> objectives;

    private static Map<Integer, Objectives> instances = new ConcurrentHashMap<>();

    private Objectives(ArrayList<Objective> objectives) {
        this.objectives = objectives;
    }

    public static Objectives getInstance(int level) {
        logger.debug("Getting Objectives instance for level {}", level);
        Objectives instance = instances.get(level);
        if (instance == null) {
            synchronized (Objectives.class) {
                instance = instances.get(level);
                if (instance == null) {
                    logger.debug("Creating new Objectives instance for level {}", level);
                    ArrayList<Objective> loadedObjectives = ObjectivesLoader.loadObjectivesFromYamlFile("level-" + level + "-objectives.yaml");
                    instance = new Objectives(loadedObjectives);
                    instances.put(level, instance);
                }
            }
        }
        return instance;
    }

    public ArrayList<Objective> getObjectives() {
        return objectives;
    }

    public void setObjectives(ArrayList<Objective> objectives) {
        this.objectives = objectives;
    }
}
