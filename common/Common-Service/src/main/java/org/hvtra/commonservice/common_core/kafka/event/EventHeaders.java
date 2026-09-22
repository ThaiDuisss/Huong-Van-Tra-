package org.hvtra.commonservice.common_core.kafka.event;

public final class EventHeaders {

    private EventHeaders() {
    }

    public static final String EVENT_ID = "event_id";
    public static final String EVENT_TYPE = "event_type";
    public static final String EVENT_VERSION = "event_version";

    public static final String SOURCE_SERVICE = "source_service";

    public static final String ENTITY_TYPE = "entity_type";
    public static final String ENTITY_ID = "entity_id";

    public static final String CORRELATION_ID = "correlation_id";
    public static final String CAUSATION_ID = "causation_id";
    public static final String SAGA_ID = "saga_id";

    public static final String OCCURRED_AT = "occurred_at";
}