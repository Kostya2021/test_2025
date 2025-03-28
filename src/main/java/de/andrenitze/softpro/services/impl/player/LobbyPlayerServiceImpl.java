package de.andrenitze.softpro.services.impl.player;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Service
@Scope("singleton")
public class LobbyPlayerServiceImpl extends BasePlayerService {
    public LobbyPlayerServiceImpl() {
        super();
    }
}