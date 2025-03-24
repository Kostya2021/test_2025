package de.andrenitze.softpro.events;

import de.andrenitze.softpro.Game;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class GlobalGameEmptyEvent extends ApplicationEvent {
    private final Game game;

    public GlobalGameEmptyEvent(Object source, Game game) {
        super(source);
        this.game = game;
    }
}
