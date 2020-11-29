package de.andrenitze.softpro.events;

import java.util.Objects;

public abstract class AbstractGameEvent {
    private EventType type;

    public EventType getType() {
        return type;
    }

    public void setType(EventType type) {
        this.type = type;
    }

    public boolean isOfType(EventType eventType) {
        return Objects.equals(this.getType(), eventType);
    }
}
