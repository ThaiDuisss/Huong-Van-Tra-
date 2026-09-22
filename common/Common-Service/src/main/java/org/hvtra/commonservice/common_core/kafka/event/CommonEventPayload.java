package org.hvtra.commonservice.kafka.event;

public interface CommonEventPayload extends EventPayload {

    String getEventId();

    void setEventId(String eventId);

    String getEventType();

    void setEventType(String eventType);

    String getEventKey();

    void setEventKey(String eventKey);
}
