package de.andrenitze.softpro.events;

import com.google.gson.annotations.SerializedName;
import de.andrenitze.softpro.types.EventType;

public class GameEvent<T> extends AbstractGameEvent {
    @SerializedName(value = "payload", alternate = {"player", "tender", "project", "employee", "tenderId", "gameOverStats"})
    private T payload;

    public GameEvent(EventType eventType) {
        super();
        setEventType(eventType);
    }

    public GameEvent() {
    }

    public T getPayload() {
        return payload;
    }

    public void setPayload(T payload) {
        this.payload = payload;
    }
}
