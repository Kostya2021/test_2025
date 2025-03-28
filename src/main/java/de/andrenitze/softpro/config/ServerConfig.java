package de.andrenitze.softpro.config;

import de.andrenitze.softpro.GameFactory;
import de.andrenitze.softpro.GameServer;
import de.andrenitze.softpro.services.LobbyPlayerService;
import de.andrenitze.softpro.services.impl.player.LobbyPlayerServiceImpl;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import static de.andrenitze.softpro.Main.logger;

@Configuration
@ComponentScan("de.andrenitze.softpro")
@PropertySource("classpath:application.properties")
public class ServerConfig {
    private final ApplicationContext parentContext;

    public ServerConfig(ApplicationContext parentContext) {
        this.parentContext = parentContext;
        logger.debug("GlobalConfig with parentContext '{}' created.", parentContext.getId());
    }

    @Bean
    public GameServer gameServer(LobbyPlayerServiceImpl lobby, GameFactory gameFactory) {
        return new GameServer(gameFactory, lobby);
    }

    @Bean
    public LobbyPlayerService lobbyPlayerService() {
        return new LobbyPlayerServiceImpl();
    }

    @Bean
    public GameFactory gameFactory() {
        return new GameFactory(parentContext);
    }

    // Messaging service for the lobby!

    // DB, Monitoring, Security...
}
