package de.andrenitze.softpro.config;

import de.andrenitze.softpro.GameServer;
import de.andrenitze.softpro.services.PlayerService;
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
public class GlobalConfig {
    private final ApplicationContext parentContext;

    public GlobalConfig(ApplicationContext parentContext) {
        this.parentContext = parentContext;
        logger.debug("GlobalConfig created.");
    }

    @Bean
    public GameServer gameServer(LobbyPlayerServiceImpl lobby) {
        return new GameServer(parentContext, lobby);
    }

    @Bean
    public PlayerService lobbyPlayerService() {
        return new LobbyPlayerServiceImpl();
    }

    // Messaging service for the lobby!

    // DB, Monitoring, Security...
}
