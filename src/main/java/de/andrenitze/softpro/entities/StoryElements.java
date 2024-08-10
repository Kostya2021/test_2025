package de.andrenitze.softpro.entities;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;

@Setter
@Getter
public class StoryElements {
    private ArrayList<StoryElement> storyElements = new ArrayList<>();

}
