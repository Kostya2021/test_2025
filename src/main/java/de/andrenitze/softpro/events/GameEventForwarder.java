package de.andrenitze.softpro.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class GameEventForwarder {
    private static final Logger log = LoggerFactory.getLogger(GameEventForwarder.class);

    private final ApplicationEventPublisher parentEventPublisher;

    public GameEventForwarder(ApplicationEventPublisher parentEventPublisher) {
        this.parentEventPublisher = parentEventPublisher;
    }

    @EventListener
    public void forwardGameOverEventToParent(GameOverEvent event) {
        log.info("↪️ GameOverEvent is forwarded to the parent context.");

        parentEventPublisher.publishEvent(
                new GlobalGameOverEvent(event.getSource(), event.getGameOverData())
        );
    }

    @EventListener
    public void forwardGameEmptyEventToParent(GameEmptyEvent event) {
        log.info("↪️ GameEmptyEvent is forwarded to the parent context.");

        parentEventPublisher.publishEvent(
                new GlobalGameEmptyEvent(event.getSource(), event.getGame())
        );
    }

}