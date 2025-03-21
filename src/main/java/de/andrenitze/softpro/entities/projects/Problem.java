package de.andrenitze.softpro.entities.projects;

import lombok.Data;

import java.util.List;

/**
 * Problems can occur in projects. They are created by the game engine and can be solved by the player.
 * Problems have a description, a status (occurred, fixed).
 * <p>
 * Problems in the game logic are questions and answers the player has to decide on.
 * Correct answers will fix the problem, wrong answers will not fix the problem, or not fix it completely.
 * The questions and answers are generate somewhere else?!?
 */
@Data
public class Problem {
    private String translationKey;
    private int occurredAt; // Tick when the problem occurred
    private int solvedAt = 0; // Tick when the problem was solved (0 = not solved)
    private List<Solution> solutions;

    public boolean isSolved() {
        return solvedAt > 0;
    }
}
