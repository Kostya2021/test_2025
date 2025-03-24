package de.andrenitze.softpro.events;

import de.andrenitze.softpro.Game;
import org.springframework.context.ApplicationEvent;

public class GlobalGameOverEvent extends ApplicationEvent {
    private final Game.GameOverData data;

    public GlobalGameOverEvent(Object source, Game.GameOverData data) {
        super(source);
        this.data = data;
    }

    public Game.GameOverData getGameOverData() {
        return data;
    }
}