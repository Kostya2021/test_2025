package de.andrenitze.softpro.services;

import lombok.Getter;
import lombok.Setter;

/**
 * Manages the game life cycle including current tick and pause/resume.
 */
@Getter
public class GameLifeCycleService {
    @Setter
    private int tick;
    private boolean paused;
    private boolean running;

    public GameLifeCycleService() {
        this.tick = 0;
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
    }
}
