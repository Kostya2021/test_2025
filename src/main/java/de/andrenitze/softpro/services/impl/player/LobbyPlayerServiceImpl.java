package de.andrenitze.softpro.services.impl.player;

import de.andrenitze.softpro.domains.players.Player;
import lombok.Getter;
import org.java_websocket.WebSocket;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import static de.andrenitze.softpro.Main.logger;

@Getter
@Service
@Scope("singleton")
public class LobbyPlayerServiceImpl extends BasePlayerService {
    public LobbyPlayerServiceImpl() {
        super();
    }

    @Override
    public void addPlayer(WebSocket webSocket, Player player) {
        if (hasWebSocket(webSocket)) {
            return; // Player already exists
        }
        players.put(webSocket, player);
        logger.debug("Added player {} to lobby.", player.getId());
    }
}