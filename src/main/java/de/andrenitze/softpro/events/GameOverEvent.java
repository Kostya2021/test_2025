package de.andrenitze.softpro.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import de.andrenitze.softpro.Game;

@Getter
public class GameOverEvent extends ApplicationEvent {
    private final transient Game.GameOverData gameOverData;

    public GameOverEvent(Object source, Game.GameOverData gameOverData) {
        super(source);
        this.gameOverData = gameOverData;
    }

}