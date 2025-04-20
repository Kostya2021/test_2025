package de.andrenitze.softpro.config;

import de.andrenitze.softpro.GameFactory;
import de.andrenitze.softpro.GameServer;
import de.andrenitze.softpro.repositories.SavegameRepository;
import de.andrenitze.softpro.services.PlayerService;
import de.andrenitze.softpro.services.impl.GameLifeCycleService;
import de.andrenitze.softpro.services.impl.player.LobbyPlayerServiceImpl;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import static de.andrenitze.softpro.Main.logger;

@Configuration
@ComponentScan("de.andrenitze.softpro")
@EnableJpaRepositories(basePackages = "de.andrenitze.softpro.repositories")
@EntityScan(basePackages = "de.andrenitze.softpro.domains")
@PropertySource("classpath:application.properties")
public class ServerConfig {
    private final ApplicationContext parentContext;

    public ServerConfig(ApplicationContext parentContext) {
        this.parentContext = parentContext;
        logger.debug("GlobalConfig with parentContext '{}' created.", parentContext.getId());
    }

    @Bean
    public GameServer gameServer(LobbyPlayerServiceImpl lobby,
                                 GameFactory gameFactory,
                                 GameLifeCycleService lifeCycleService,
                                 SavegameRepository savegameRepository) {
        return new GameServer(gameFactory, lobby, lifeCycleService, savegameRepository);
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

