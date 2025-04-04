package de.andrenitze.softpro.events;

import de.andrenitze.softpro.Game;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class GameOverEvent extends ApplicationEvent {
    private final transient Game.GameOverData gameOverData;

    public GameOverEvent(Object source, Game.GameOverData gameOverData) {
        super(source);
        this.gameOverData = gameOverData;
    }

}