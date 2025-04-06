package de.andrenitze.softpro.services;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Manages the game life cycle including current tick and pause/resume.
 */
@Getter
public class GameLifeCycleService {
    @Setter
    private int tick;
    private boolean paused;
    private boolean running;
    @Getter
    private LocalDate currentDate;

    public GameLifeCycleService() {
        this.tick = 0;
        this.currentDate = LocalDate.now();
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
