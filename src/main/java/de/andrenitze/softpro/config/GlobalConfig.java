package de.andrenitze.softpro.config;

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
    public GlobalConfig(ApplicationContext parentContext) {
        logger.debug("GlobalConfig created.");
    }

    // Messaging service for the lobby!

    // DB, Monitoring, Security...
}
