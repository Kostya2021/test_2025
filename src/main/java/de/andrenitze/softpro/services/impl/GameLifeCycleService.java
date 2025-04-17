package de.andrenitze.softpro.services.impl;

import de.andrenitze.softpro.config.Config;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Manages the game life cycle including current tick and pause/resume.
 */
@Getter
public class GameLifeCycleService {
    @Getter
    private final int gameSpeedInMilliseconds;
    @Setter
    private int tick;
    private boolean paused;
    private boolean running;
    @Getter
    private LocalDate currentDate;
    @Getter @Setter
    private int level;

    public GameLifeCycleService() {
        this.tick = 0;
        this.currentDate = LocalDate.now();

        String gameSpeed = Config.getProperty("GAME_SPEED_IN_MILLISECONDS");
        gameSpeedInMilliseconds = Integer.parseInt(gameSpeed);
    }

    public void pause() {
        paused = true;
    }

    public void resume() {
        paused = false;
    }

    public void run() {
        running = true;
    }

    public void nextTick() {
        if (paused) {
            return;
        }
        tick++;
        currentDate = currentDate.plusDays(1);
    }
}
