package de.andrenitze.softpro;

import de.andrenitze.softpro.config.GameConfig;
import de.andrenitze.softpro.services.impl.DecisionService;
import de.andrenitze.softpro.types.GameOverStatsDAO;
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