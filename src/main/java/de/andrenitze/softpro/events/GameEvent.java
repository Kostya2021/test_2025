package de.andrenitze.softpro.events;

import com.google.gson.annotations.SerializedName;
import de.andrenitze.softpro.types.EventType;

public class GameEvent<T> {
    @SerializedName(value = "payload", alternate = {"player", "tender", "project", "employee", "tenderId", "gameOverStats", "talentMarket"})
    private T payload;

    private EventType type;

    public GameEvent(EventType eventType) {
        super();
        setType(eventType);
    }

    public GameEvent() {
    }

    public T getPayload() {
        return payload;
    }

    public void setPayload(T payload) {
        this.payload = payload;
    }

    public EventType getType() {
        return type;
    }

    public void setType(EventType type) {
        this.type = type;
    }
}
