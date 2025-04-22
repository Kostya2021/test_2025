package de.andrenitze.softpro.domains.story;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StoryElementsLoader {
    private static final Logger log = LoggerFactory.getLogger(StoryElementsLoader.class);
    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    private final Map<Integer, ArrayList<StoryElement>> levelStoryElements = new HashMap<>();

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
            levelStoryElements.put(level, extractedStoryElements.getElements());
            log.debug("Loaded {} story elements for level {}.", extractedStoryElements.getElements().size(), level);
        } catch (IOException e) {
            log.error("Error while loading story elements from yaml file: {}", e.getMessage());
        }
    }

    public List<StoryElement> getStoryElementsForLevel(int level) {
        loadStoryElementsFromYamlFile(level);
        return levelStoryElements.getOrDefault(level, new ArrayList<>());
    }
}
