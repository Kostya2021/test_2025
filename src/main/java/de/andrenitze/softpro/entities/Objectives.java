package de.andrenitze.softpro.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import de.andrenitze.softpro.Game;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class Objectives {
    @JsonProperty
    private ArrayList<Objective> objectives;

    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    public void loadObjectivesFromYamlFile() {
        try {
            Objectives extractedObjectives = mapper.readValue(new File("src/main/resources/objectives.yaml"), Objectives.class);
            setObjectives(extractedObjectives.getObjectives());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public ArrayList<Objective> getObjectives() {
        return objectives;
    }

    public void setObjectives(ArrayList<Objective> objectives) {
        this.objectives = objectives;
    }

    public void calculateCompletedSteps(Game game) {
        // TODO Do some calculation on completion, then send some update to the player
        for (Objective objective: objectives) {
            objective.setCompletedSteps(objective.getCompletedSteps());
        }
    }
}