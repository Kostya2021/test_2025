package de.andrenitze.softpro;

import de.andrenitze.softpro.config.GameConfig;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class GameFactory {

    private final ApplicationContext parentContext;

    public GameFactory(ApplicationContext parentContext) {
        this.parentContext = parentContext;
    }

    public AnnotationConfigApplicationContext buildGameInstance() {
        AnnotationConfigApplicationContext gameContext = new AnnotationConfigApplicationContext();
        gameContext.setParent(parentContext); // Inherit global context
        gameContext.register(GameConfig.class); // Game-specific Beans

        // Explicitly register the parent context as a resolvable dependency so that the
        // game context can access the parent context's beans (e.g., to forward GameOverEvent and GameEmptyEvent
        // to GameServer context).
        gameContext.getBeanFactory().registerResolvableDependency(
                ApplicationEventPublisher.class, parentContext);

        gameContext.refresh();
        return gameContext;
    }
}