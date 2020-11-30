package de.andrenitze.softpro.entities;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;

/**
 * {@link ObjectivesManager} hands out objectives to players and checks for active objectives' progress after
 * relevant de.andrenitze.softpro.events. Hands out rewards to players upon objective completion.
 *
 * Objectives can be grouped in missions. Missions are completed when all objectives are completed.
 */
public class ObjectivesManager {
    private Objectives objectives;

    void spawnObjective() {

    }

    void evaluateEvent() {

    }

    public ObjectivesManager() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            objectives = mapper.readValue(new File("src/main/resources/objectives.yaml"), Objectives.class);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Objectives getObjectives() {
        return objectives;
    }

    public void setObjectives(Objectives objectives) {
        this.objectives = objectives;
    }
}
