package org.hvtra.orderservice.integration.consumer;

import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.common_core.kafka.event.DebeziumOutboxEventPayloadParser;
import org.hvtra.orderservice.dto.event.PaymentOrderEventPayload;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentEventPayloadParser {

    private final DebeziumOutboxEventPayloadParser parser;

    public PaymentOrderEventPayload parse(String rawPayload) {
        return parseCreateEvent(rawPayload).orElseThrow(() ->
                new IllegalArgumentException("Payment event is not a Debezium create event")
        );
    }

    public Optional<PaymentOrderEventPayload> parseCreateEvent(String rawPayload) {
        return parser.parseCreateEvent(
                rawPayload,
                PaymentOrderEventPayload.class,
                "payment event",
                (event, metadata) -> {
                    if (isBlank(event.getSagaId())) {
                        event.setSagaId(metadata.sagaId());
                    }
                    if (isBlank(event.getPaymentId())) {
                        event.setPaymentId(metadata.aggregateId());
                    }
                }
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
