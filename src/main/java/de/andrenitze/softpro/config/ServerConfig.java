package de.andrenitze.softpro.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import de.andrenitze.softpro.GameFactory;
import de.andrenitze.softpro.GameServer;
import de.andrenitze.softpro.repositories.SavegameRepository;
import de.andrenitze.softpro.services.PlayerService;
import de.andrenitze.softpro.services.impl.player.LobbyPlayerServiceImpl;

@Configuration
@Slf4j
public class ServerConfig {
    private final ApplicationContext parentContext;

    public ServerConfig(ApplicationContext parentContext) {
        this.parentContext = parentContext;
        log.debug("GlobalConfig with parentContext '{}' created.", parentContext.getId());
    }

    @Bean
    public GameServer gameServer(LobbyPlayerServiceImpl lobby,
                                 GameFactory gameFactory,
                                 SavegameRepository savegameRepository) {
        return new GameServer(gameFactory, lobby, savegameRepository);
    }

    @Bean
    public PlayerService lobbyPlayerService() {
        return new LobbyPlayerServiceImpl();
    }

    @Bean
    public GameFactory gameFactory() {
        return new GameFactory(parentContext);
    }

    // Messaging service for the lobby!

    // DB, Monitoring, Security...
}

