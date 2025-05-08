package de.andrenitze.softpro.events;

import org.springframework.context.ApplicationEventPublisher;

public class GameEventPublisher {
    private final ApplicationEventPublisher publisher;

    public GameEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publishGameOverEvent(GameOverEvent gameOverEvent) {
        publisher.publishEvent(gameOverEvent);
    }

    public void publishGameEmptyEvent(GlobalGameEmptyEvent gameEmptyEvent) {
        publisher.publishEvent(gameEmptyEvent);
    }
}