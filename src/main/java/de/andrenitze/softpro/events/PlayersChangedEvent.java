package de.andrenitze.softpro.events;

import de.andrenitze.softpro.Player;
import lombok.Getter;
import org.java_websocket.WebSocket;
import org.springframework.context.ApplicationEvent;

import java.util.Map;

import static de.andrenitze.softpro.Main.logger;

@Getter
public class PlayersChangedEvent extends ApplicationEvent {
    private final transient Map<WebSocket, Player> players;

    public PlayersChangedEvent(Object source, Map<WebSocket, Player> players) {
        super(source);
        this.players = players;
        logger.debug("PlayersChangedEvent (size: {}) fired.", players.size());
    }

}