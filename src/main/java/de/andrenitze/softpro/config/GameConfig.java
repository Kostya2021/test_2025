package de.andrenitze.softpro.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("de.andrenitze.softpro")
@Slf4j
public class GameConfig {
    public GameConfig(ApplicationContext parentContext) {
        log.debug("GameConfig with parentContext '{}' created.", parentContext.getDisplayName());
    }
}