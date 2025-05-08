package de.andrenitze.softpro.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import de.andrenitze.softpro.Game;

@Getter
public class GlobalGameEmptyEvent extends ApplicationEvent {
    private final transient Game game;

    public GlobalGameEmptyEvent(Object source, Game game) {
        super(source);
        this.game = game;
    }
}
