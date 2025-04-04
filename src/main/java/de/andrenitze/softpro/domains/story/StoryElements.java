package de.andrenitze.softpro.domains.story;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;

@Setter
@Getter
public class StoryElements {
    private ArrayList<StoryElement> elements = new ArrayList<>();

}
