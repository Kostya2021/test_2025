package de.andrenitze.softpro.config;

import de.andrenitze.softpro.GameFactory;
import de.andrenitze.softpro.GameServer;
import de.andrenitze.softpro.events.GameEventPublisher;
import de.andrenitze.softpro.level.LevelConfigurator;
import de.andrenitze.softpro.repositories.SavegameRepository;
import de.andrenitze.softpro.services.PlayerService;
import de.andrenitze.softpro.services.impl.player.LobbyPlayerServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
                                 SavegameRepository savegameRepository,
                                 LevelConfigurator levelConfigurator) {
        return new GameServer(gameFactory, lobby, savegameRepository, levelConfigurator);
    }

    @Bean
    public PlayerService lobbyPlayerService() {
        return new LobbyPlayerServiceImpl();
    }

    @Bean
    public GameFactory gameFactory() {
        return new GameFactory(parentContext);
    }

    @Bean
    public GameEventPublisher gameEventPublisher(ApplicationEventPublisher publisher) {
        return new GameEventPublisher(publisher);
    }
}

