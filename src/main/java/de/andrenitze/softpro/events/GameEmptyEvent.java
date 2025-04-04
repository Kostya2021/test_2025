package de.andrenitze.softpro.events;

import de.andrenitze.softpro.Game;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class GameEmptyEvent extends ApplicationEvent {
    private final transient Game game;

    public GameEmptyEvent(Object source, Game game) {
        super(source);
        this.game = game;
    }
}
