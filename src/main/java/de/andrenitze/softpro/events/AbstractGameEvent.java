package de.andrenitze.softpro.events;

import java.util.Objects;

public abstract class AbstractGameEvent {
    private EventType type;

    public EventType getEventType() {
        return type;
    }

    public void setEventType(EventType eventType) {
        this.type = eventType;
    }

    public boolean isOfType(EventType eventType) {
        return Objects.equals(this.getEventType(), eventType);
    }
}
