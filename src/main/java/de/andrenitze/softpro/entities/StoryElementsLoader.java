package de.andrenitze.softpro.entities;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static de.andrenitze.softpro.Main.logger;

public class StoryElementsLoader {
    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    private static final int NUMBER_OF_LEVELS = 2;
    private final Map<Integer, ArrayList<StoryElement>> levelStoryElements = new HashMap<>();

    public StoryElementsLoader() {
        for (int i = 1; i <= NUMBER_OF_LEVELS; i++) {
            loadStoryElementsFromYamlFile(i);
        }
    }

    private InputStream getFileFromResourceAsStream(int level) {
        String filename = "level-" + level + "-story.yaml";

        ClassLoader classLoader = getClass().getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(filename);

        if (inputStream == null) {
            throw new IllegalArgumentException("file not found! " + filename);
        } else {
            return inputStream;
        }
    }

    private void loadStoryElementsFromYamlFile(int level) {
        try {
            StoryElements extractedStoryElements = mapper.readValue(getFileFromResourceAsStream(level), StoryElements.class);
            levelStoryElements.put(level, extractedStoryElements.getStoryElements());
            logger.debug("Loaded {} story elements for level {}", extractedStoryElements.getStoryElements().size(), level);
        } catch (IOException e) {
            logger.error("Error while loading story elements from yaml file: {}", e.getMessage());
        }
    }

    public ArrayList<StoryElement> getStoryElementsForLevel(int level) {
        return levelStoryElements.getOrDefault(level, new ArrayList<>());
    }
}
