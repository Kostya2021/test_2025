package de.andrenitze.softpro.services.impl.player;

import de.andrenitze.softpro.domains.players.Player;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.WebSocket;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Getter
@Service
@Scope("singleton")
@Slf4j
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
        log.debug("Added player {} to lobby.", player.getId());
    }
}