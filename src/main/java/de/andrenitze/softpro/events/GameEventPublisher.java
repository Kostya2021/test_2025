package de.andrenitze.softpro.events;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.annotation.Primary;

@Primary
public class GameEventPublisher implements ApplicationEventPublisherAware {
    private ApplicationEventPublisher publisher;

    @Override
    public void setApplicationEventPublisher(@NotNull ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publishGameOverEvent(GameOverEvent gameOverEvent) {
        publisher.publishEvent(gameOverEvent);
    }

    public void publishGameEmptyEvent(GlobalGameEmptyEvent globalGameEmptyEvent) {
        publisher.publishEvent(globalGameEmptyEvent);
    }
}