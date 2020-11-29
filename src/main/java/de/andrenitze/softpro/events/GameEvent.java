package de.andrenitze.softpro.events;

import com.google.gson.annotations.SerializedName;

public class GameEvent<T> extends AbstractGameEvent {
  private T objectType;

  @SerializedName(value="payload", alternate={"player", "tender", "project", "employee", "tenderId"})
  private T payload;

  public T getPayload() {
    return payload;
  }

  public void setPayload(T payload) {
    this.payload = payload;
  }

  public T getObjectType() {
    return objectType;
  }

  public void setPayloadType(T objectType) {
    this.objectType = objectType;
  }
}
