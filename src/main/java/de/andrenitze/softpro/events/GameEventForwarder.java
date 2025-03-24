package de.andrenitze.softpro.events;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import static de.andrenitze.softpro.Main.logger;

@Component
public class GameEventForwarder {

    private final ApplicationEventPublisher parentEventPublisher;

    public GameEventForwarder(ApplicationEventPublisher parentEventPublisher) {
        this.parentEventPublisher = parentEventPublisher;
    }

    @EventListener
    public void forwardGameOverEventToParent(GameOverEvent event) {
        logger.info("↪️ GameOverEvent is forwarded to the parent context.");

        parentEventPublisher.publishEvent(
                new GlobalGameOverEvent(event.getSource(), event.getGameOverData())
        );
    }

    @EventListener
    public void forwardGameEmptyEventToParent(GameEmptyEvent event) {
        logger.info("↪️ GameEmptyEvent is forwarded to the parent context.");

        parentEventPublisher.publishEvent(
                new GlobalGameEmptyEvent(event.getSource(), event.getGame())
        );
    }

}