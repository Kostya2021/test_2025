package de.andrenitze.softpro.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import static de.andrenitze.softpro.Main.logger;

public class StoryElements {
    @JsonProperty
    private ArrayList<StoryElement> storyElements;

    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    private InputStream getFileFromResourceAsStream() {

        // The class loader that loaded the class
        ClassLoader classLoader = getClass().getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream("story.yaml");

        // the stream holding the file content
        if (inputStream == null) {
            throw new IllegalArgumentException("file not found! " + "story.yaml");
        } else {
            return inputStream;
        }

    }

    public void loadStoryElementsFromYamlFile() {
        try {
            StoryElements extractedStoryElements = mapper.readValue(getFileFromResourceAsStream(), StoryElements.class);
            setStoryElements(extractedStoryElements.getStoryElements());
        } catch (IOException e) {
            logger.error("Error while loading story elements from yaml file: {}", e.getMessage());
        }
    }

    public ArrayList<StoryElement> getStoryElements() {
        return storyElements;
    }

    public void setStoryElements(ArrayList<StoryElement> storyElements) {
        this.storyElements = storyElements;
    }
}