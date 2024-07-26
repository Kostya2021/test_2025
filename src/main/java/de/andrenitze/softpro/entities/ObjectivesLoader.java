package de.andrenitze.softpro.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import static de.andrenitze.softpro.Main.logger;

public class ObjectivesLoader {
    @JsonProperty
    private ArrayList<Objective> objectives;

    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    public static ArrayList<Objective> loadObjectivesFromYamlFile(String filename) {
        try {
            InputStream yamlStream = getFileFromResourceAsStream(filename);
            ObjectivesLoader tempLoader = mapper.readValue(yamlStream, ObjectivesLoader.class);
            logger.debug("Loaded objectives from file {}", filename);
            return tempLoader.getObjectives();
        } catch (IOException e) {
            logger.error("Error while loading objectives from yaml file: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    private static InputStream getFileFromResourceAsStream(String fileName) {
        ClassLoader classLoader = ObjectivesLoader.class.getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(fileName);
        if (inputStream == null) {
            throw new IllegalArgumentException("file not found! " + fileName);
        } else {
            return inputStream;
        }
    }

    public ArrayList<Objective> getObjectives() {
        return objectives;
    }
}
