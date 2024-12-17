package de.andrenitze.softpro.entities;

import lombok.Data;

import static de.andrenitze.softpro.Main.logger;

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
    private String description;
    private boolean occurred = true;
    private boolean fixed = false;

    public Problem() {
        // Generate a random problem
        this.description = generateDescription();
        logger.debug("Problem created: {}", this.description);
    }

    private String generateDescription() {
        return "Random problem";
    }
}
