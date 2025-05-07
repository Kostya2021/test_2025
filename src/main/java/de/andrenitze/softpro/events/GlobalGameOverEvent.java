package de.andrenitze.softpro.events;

import org.springframework.context.ApplicationEvent;

import de.andrenitze.softpro.Game;

public class GlobalGameOverEvent extends ApplicationEvent {
    private final transient Game.GameOverData data;

    public GlobalGameOverEvent(Object source, Game.GameOverData data) {
        super(source);
        this.data = data;
    }

    public Game.GameOverData getGameOverData() {
        return data;
    }
}