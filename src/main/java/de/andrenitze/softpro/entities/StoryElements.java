package de.andrenitze.softpro.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;

public class StoryElements {
    @JsonProperty("storyElements")
    private ArrayList<StoryElement> storyElements = new ArrayList<>();

    public ArrayList<StoryElement> getStoryElements() {
        return storyElements;
    }

    public void setStoryElements(ArrayList<StoryElement> storyElements) {
        this.storyElements = storyElements;
    }
}
