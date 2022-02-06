package de.andrenitze.softpro.events;

import com.google.gson.annotations.SerializedName;
import de.andrenitze.softpro.types.EventType;

public class GameEvent<T> {
    // TODO Alternate fields are not allowed here anymore! Change code to always have a "payload"-field! --> "{ payload : {projectId: 3}...}" !
    @SerializedName(value = "payload", alternate = {"player", "tender", "project", "employee", "tenderId", "gameOverStats"})
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

    public boolean isOfType(T type) {
        return (type == this.type);
    }
}
