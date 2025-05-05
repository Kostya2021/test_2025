package de.andrenitze.softpro.events;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

@Slf4j
public class GameEventForwarder {
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