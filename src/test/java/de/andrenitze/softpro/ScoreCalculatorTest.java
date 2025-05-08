package de.andrenitze.softpro;

import de.andrenitze.softpro.domains.players.Player;
import de.andrenitze.softpro.domains.projects.Problem;
import de.andrenitze.softpro.domains.projects.Project;
import de.andrenitze.softpro.domains.projects.Solution;
import de.andrenitze.softpro.services.ProjectService;
import de.andrenitze.softpro.services.impl.ScoreCalculator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScoreCalculatorTest {

    @Test
    void testNoProjects() {
        Game game = mock(Game.class);
        Player player = mock(Player.class);
        ProjectService ps = mock(ProjectService.class);

        when(game.getProjectService()).thenReturn(ps);
        when(ps.getProjectsByPlayer(player)).thenReturn(List.of());

        ScoreCalculator sc = new ScoreCalculator();
        int score = sc.calculateScore(player, game);
        assertEquals(100, score); // No penalty for no projects
    }

    @Test
    void testThreeProjectsNoProblems() {
        Game game = mock(Game.class);
        Player player = mock(Player.class);
        ProjectService ps = mock(ProjectService.class);

        when(game.getProjectService()).thenReturn(ps);

        Project project1 = mock(Project.class);
        Project project2 = mock(Project.class);
        Project project3 = mock(Project.class);

        when(project1.isCompleted()).thenReturn(true);
        when(project2.isCompleted()).thenReturn(true);
        when(project3.isCompleted()).thenReturn(true);

        when(ps.getProjectsByPlayer(player)).thenReturn(List.of(project1, project2, project3));

        ScoreCalculator sc = new ScoreCalculator();
        int score = sc.calculateScore(player, game);
        assertEquals(100, score); // 100% projects completed, 0% problems
    }

    @Test
    void testAllProjectsCompletedAllCorrect() {
        Game game = mock(Game.class);
        Player player = mock(Player.class);
        ProjectService ps = mock(ProjectService.class);
        when(game.getProjectService()).thenReturn(ps);

        Project project = mock(Project.class);
        when(project.isCompleted()).thenReturn(true);

        Problem problem = mock(Problem.class);
        Solution solution = mock(Solution.class);
        when(solution.getType()).thenReturn(Solution.SolutionType.CORRECT);
        when(problem.getSolutions()).thenReturn(List.of(solution));
        when(project.getProblems()).thenReturn(List.of(problem));

        when(ps.getProjectsByPlayer(player)).thenReturn(List.of(project, project)); // 2 identical completed projects

        ScoreCalculator sc = new ScoreCalculator();
        int score = sc.calculateScore(player, game);
        assertEquals(100, score); // 100% projects completed, 100% correct solutions
    }

    @Test
    void testPartialSolutionsImpact() {
        Game game = mock(Game.class);
        Player player = mock(Player.class);
        ProjectService ps = mock(ProjectService.class);
        when(game.getProjectService()).thenReturn(ps);

        Project project = mock(Project.class);
        when(project.isCompleted()).thenReturn(true);

        Problem problem1 = mock(Problem.class);
        Problem problem2 = mock(Problem.class);
        Problem problem3 = mock(Problem.class);

        Solution correct = mock(Solution.class);
        when(correct.getType()).thenReturn(Solution.SolutionType.CORRECT);

        Solution partial = mock(Solution.class);
        when(partial.getType()).thenReturn(Solution.SolutionType.PARTIALLY_CORRECT);

        Solution incorrect = mock(Solution.class);
        when(incorrect.getType()).thenReturn(Solution.SolutionType.INCORRECT);

        when(problem1.getSolutions()).thenReturn(List.of(correct));
        when(problem2.getSolutions()).thenReturn(List.of(partial));
        when(problem3.getSolutions()).thenReturn(List.of(incorrect));

        when(project.getProblems()).thenReturn(List.of(problem1, problem2, problem3));
        when(ps.getProjectsByPlayer(player)).thenReturn(List.of(project));

        ScoreCalculator sc = new ScoreCalculator();
        int score = sc.calculateScore(player, game);

        // Projects: 1/1 => 60 Points
        // Problems: 1/3 correct (+33.3), 1/3 partial (-3.3), 1/3 incorrect (-1.6) → ≈ 28.3 * 0.4 = 11.3
        // Expected: ~71
        assertTrue(score >= 70 && score <= 72);
    }

    @Test
    void testNoCompletedProjects() {
        Game game = mock(Game.class);
        Player player = mock(Player.class);
        ProjectService ps = mock(ProjectService.class);
        when(game.getProjectService()).thenReturn(ps);

        Project incompleteProject = mock(Project.class);
        when(incompleteProject.isCompleted()).thenReturn(false);
        when(ps.getProjectsByPlayer(player)).thenReturn(List.of(incompleteProject, incompleteProject));

        ScoreCalculator sc = new ScoreCalculator();
        int score = sc.calculateScore(player, game);

        assertEquals(100, score); // No projects completed, no penalty
    }

    @Test
    void testCancelledProjectsAffectProjectScore() {
        Game game = mock(Game.class);
        Player player = mock(Player.class);
        ProjectService projectService = mock(ProjectService.class);

        when(game.getProjectService()).thenReturn(projectService);

        Project completedProject = mock(Project.class);
        Project cancelledProject = mock(Project.class);
        Project runningProject = mock(Project.class);

        // Setup: 1 abgeschlossen, 1 abgebrochen, 1 läuft noch
        when(completedProject.isCompleted()).thenReturn(true);
        when(completedProject.getCancelledAt()).thenReturn(0);

        when(cancelledProject.isCompleted()).thenReturn(false);
        when(cancelledProject.getCancelledAt()).thenReturn(123456789); // marked as cancelled

        when(runningProject.isCompleted()).thenReturn(false);
        when(runningProject.getCancelledAt()).thenReturn(0); // still running

        when(projectService.getProjectsByPlayer(player)).thenReturn(
                List.of(completedProject, cancelledProject, runningProject)
        );

        ScoreCalculator calculator = new ScoreCalculator();
        int score = calculator.calculateScore(player, game);

        // Erwartung:
        // - 1 completed, 1 failed → 1/2 → 50% → 50 * 0.6 = 30
        // - keine Probleme → volle 40 Punkte → 40
        // - Gesamt: 70
        assertEquals(70, score);
    }
}
