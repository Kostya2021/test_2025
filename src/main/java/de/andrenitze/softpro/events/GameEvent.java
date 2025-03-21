package de.andrenitze.softpro.events;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
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

}
