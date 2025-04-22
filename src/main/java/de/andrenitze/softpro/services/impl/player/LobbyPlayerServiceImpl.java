package de.andrenitze.softpro.services.impl.player;

import de.andrenitze.softpro.domains.players.Player;
import lombok.Getter;
import org.java_websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Getter
@Service
@Scope("singleton")
public class LobbyPlayerServiceImpl extends BasePlayerService {
    private static final Logger log = LoggerFactory.getLogger(LobbyPlayerServiceImpl.class);
    public LobbyPlayerServiceImpl() {
        super();
    }

    @Override
    public void addPlayer(WebSocket webSocket, Player player) {
        if (hasWebSocket(webSocket)) {
            return; // Player already exists
        }
        players.put(webSocket, player);
        log.debug("Added player {} to lobby.", player.getId());
    }
}