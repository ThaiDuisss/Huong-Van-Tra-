package org.hvtra.commonservice.kafka.outbox.dto;

import java.util.UUID;

public record ClaimedOutboxMessage(
        UUID id,
        String eventType,
        UUID eventKey,
        String payload
) {
    public String kafkaKey() {
        return eventKey.toString();
    }
}