package de.andrenitze.softpro;

import de.andrenitze.softpro.config.GameConfig;
import de.andrenitze.softpro.events.GameEventPublisher;
import de.andrenitze.softpro.services.impl.DecisionService;
import de.andrenitze.softpro.types.GameOverStatsDAO;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class GameFactory {
    private final ApplicationContext parentContext;

    public GameFactory(ApplicationContext parentContext) {
        this.parentContext = parentContext;
    }

    public AnnotationConfigApplicationContext buildGameInstance() {
        AnnotationConfigApplicationContext gameContext = new AnnotationConfigApplicationContext();
        gameContext.setParent(parentContext);
        gameContext.register(GameConfig.class);

        // Register parent beans for dependency injection into the game context
        gameContext.getBeanFactory().registerResolvableDependency(
                ApplicationEventPublisher.class, parentContext
        );
        gameContext.getBeanFactory().registerResolvableDependency(
                GameEventPublisher.class, parentContext.getBean(GameEventPublisher.class)
        );

        gameContext.refresh();
        return gameContext;
    }

    public GameOverStatsDAO getGameOverStatsDAO() {
        // This method is used to get the GameOverStatsDAO bean from the parent context.
        // It is necessary because the GameOverStatsDAO bean is not defined in the game context.
        // The game context is a child of the parent context, so it can access beans from the parent context.
        return parentContext.getBean(GameOverStatsDAO.class);
    }

    public DecisionService getDecisionService() {
        // This method is used to get the DecisionService bean from the parent context.
        // It is necessary because the DecisionService bean is not defined in the game context.
        // The game context is a child of the parent context, so it can access beans from the parent context.
        return parentContext.getBean(DecisionService.class);
    }
}