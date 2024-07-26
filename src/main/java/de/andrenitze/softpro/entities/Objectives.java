package de.andrenitze.softpro.entities;

import java.util.ArrayList;

public class Objectives {
    private ArrayList<Objective> objectives;

    private static Objectives instance;

    private Objectives(ArrayList<Objective> objectives) {
        this.objectives = objectives;
    }

    public static Objectives getInstance(int level) {
        if (instance == null) {
            synchronized (Objectives.class) {
                if (instance == null) {
                    ArrayList<Objective> loadedObjectives = ObjectivesLoader.loadObjectivesFromYamlFile("level-" + level + "-objectives.yaml");
                    instance = new Objectives(loadedObjectives);
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
