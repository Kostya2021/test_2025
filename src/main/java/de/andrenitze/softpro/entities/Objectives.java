package de.andrenitze.softpro.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import static de.andrenitze.softpro.Main.logger;

public class Objectives {
    @JsonProperty
    private ArrayList<Objective> objectives;

    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    private InputStream getFileFromResourceAsStream(String fileName) {

        // The class loader that loaded the class
        ClassLoader classLoader = getClass().getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(fileName);

        // the stream holding the file content
        if (inputStream == null) {
            throw new IllegalArgumentException("file not found! " + fileName);
        } else {
            return inputStream;
        }

    }

    public void loadObjectivesFromYamlFile() {
        try {
            Objectives extractedObjectives = mapper.readValue(getFileFromResourceAsStream("objectives.yaml"), Objectives.class);
            setObjectives(extractedObjectives.getObjectives());
        } catch (IOException e) {
            logger.error("Error while loading objectives from yaml file: {}", e.getMessage());
        }
    }

    public ArrayList<Objective> getObjectives() {
        return objectives;
    }

    public void setObjectives(ArrayList<Objective> objectives) {
        this.objectives = objectives;
    }
}