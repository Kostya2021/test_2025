package de.andrenitze.softpro.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import de.andrenitze.softpro.Game;

@Getter
public class GameEmptyEvent extends ApplicationEvent {
    private final transient Game game;

    public GameEmptyEvent(Object source, Game game) {
        super(source);
        this.game = game;
    }
}
