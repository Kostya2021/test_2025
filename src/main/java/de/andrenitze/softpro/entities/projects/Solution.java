package de.andrenitze.softpro.entities.projects;

import lombok.Data;

@Data
public class Solution {
    private String translationKey;
    private String feedbackKey;
    private SolutionType type;

    public enum SolutionType { CORRECT, PARTIALLY_CORRECT, INCORRECT }

    public Solution(String translationKey, String feedbackKey, SolutionType type) {
        this.translationKey = translationKey;
        this.feedbackKey = feedbackKey;
        this.type = type;
    }
}