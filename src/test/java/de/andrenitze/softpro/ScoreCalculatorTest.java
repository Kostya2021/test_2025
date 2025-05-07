package de.andrenitze.softpro;

import de.andrenitze.softpro.Game;
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
        assertEquals(0, score);
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

        assertEquals(0, score); // No projects completed
    }
}
