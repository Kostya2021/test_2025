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
     */
    protected float calculateProjectScore(List<Project> projects) {
        long completed = projects.stream().filter(Project::isCompleted).count();
        long failed = projects.stream().filter(this::isFailed).count();

        if (completed + failed == 0) return 100;

        float successRate = (float) completed / (completed + failed);
        return successRate * 100;
    }

    private boolean isFailed(Project project) {
        return !project.isCompleted() && project.getCancelledAt() != 0;
    }

    /**
     * Calculates the score portion based on how well the player solved problems.
     * If no problems exist, full score is awarded for this part.
     */
    protected float calculateProblemScore(List<Project> projects) {
        int[] counts = countSolutionTypes(projects);
        int correct = counts[0];
        int partial = counts[1];
        int incorrect = counts[2];
        int total = counts[3];

        if (total == 0) return 100;

        float correctRate = (float) correct / total;
        float partialRate = (float) partial / total;
        float incorrectRate = (float) incorrect / total;

        return (correctRate * 100f) - (partialRate * 10f) - (incorrectRate * 5f);
    }

    /**
     * Counts the number of correct, partially correct, and incorrect solutions, and total problems.
     */
    private int[] countSolutionTypes(List<Project> projects) {
        return projects.stream()
                .filter(Project::isCompleted)
                .flatMap(project -> safeList(project.getProblems()).stream())
                .map(this::evaluateProblem)
                .reduce(new int[4], this::combineCounts);
    }

    private int[] evaluateProblem(Problem problem) {
        int correct = 0;
        int partial = 0;
        int incorrect = 0;
        List<Solution> solutions = safeList(problem.getSolutions());

        for (Solution solution : solutions) {
            var type = solution.getType();
            if (type == null) continue;

            switch (type) {
                case CORRECT -> correct++;
                case PARTIALLY_CORRECT -> partial++;
                case INCORRECT -> incorrect++;
            }
        }

        return new int[]{correct, partial, incorrect, 1}; // 1 = 1 problem counted
    }

    private int[] combineCounts(int[] a, int[] b) {
        return new int[]{
                a[0] + b[0], // correct
                a[1] + b[1], // partial
                a[2] + b[2], // incorrect
                a[3] + b[3]  // total problems
        };
    }

    private <T> List<T> safeList(List<T> maybeNull) {
        return maybeNull != null ? maybeNull : List.of();
    }
}