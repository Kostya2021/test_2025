package de.andrenitze.softpro.services.impl;

import de.andrenitze.softpro.Game;
import de.andrenitze.softpro.domains.players.Player;
import de.andrenitze.softpro.domains.projects.Problem;
import de.andrenitze.softpro.domains.projects.Project;
import de.andrenitze.softpro.domains.projects.Solution;
import java.util.List;

public class ScoreCalculator {
    private static final float PROJECT_WEIGHT = 0.6f;
    private static final float PROBLEM_WEIGHT = 0.4f;

    /**
     * Calculates a performance score for a given player in the current game state.
     * The score ranges from 0 to 100 and reflects project completion and problem-solving performance.
     */
    public int calculateScore(Player player, Game game) {
        List<Project> projects = game.getProjectService().getProjectsByPlayer(player);

        float projectScore = calculateProjectScore(projects) * PROJECT_WEIGHT;
        float problemScore = calculateProblemScore(projects) * PROBLEM_WEIGHT;

        float totalScore = projectScore + problemScore;
        return Math.round(Math.clamp(totalScore, 0, 100));
    }

    /**
     * Calculates the score portion based on project completion rate.
     * Each completed project contributes positively to the score.
     */
    protected float calculateProjectScore(List<Project> projects) {
        int total = projects.size();
        if (total == 0) return 0;

        long completed = projects.stream().filter(Project::isCompleted).count();
        return ((float) completed / total) * 100;
    }

    /**
     * Calculates the score portion based on how well the player solved problems.
     * Correct solutions improve score; partial or incorrect reduce it.
     */
    protected float calculateProblemScore(List<Project> projects) {
        int[] counts = countSolutionTypes(projects);
        int correct = counts[0];
        int partial = counts[1];
        int incorrect = counts[2];
        int total = counts[3];

        if (total == 0) return 0;

        float correctRate = (float) correct / total;
        float partialRate = (float) partial / total;
        float incorrectRate = (float) incorrect / total;

        return (correctRate * 100) - (partialRate * 10) - (incorrectRate * 5);
    }

    private int[] countSolutionTypes(List<Project> projects) {
        int correct = 0;
        int partial = 0;
        int incorrect = 0;
        int total = 0;

        for (Project project : projects) {
            if (!project.isCompleted()) continue;

            for (Problem problem : project.getProblems()) {
                total++;
                if (problem.getSolutions() == null) continue;

                for (Solution solution : problem.getSolutions()) {
                    switch (solution.getType()) {
                        case CORRECT -> correct++;
                        case PARTIALLY_CORRECT -> partial++;
                        case INCORRECT -> incorrect++;
                    }
                }
            }
        }

        return new int[]{correct, partial, incorrect, total};
    }
}
