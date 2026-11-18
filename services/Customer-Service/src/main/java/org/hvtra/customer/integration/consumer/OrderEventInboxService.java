package org.hvtra.customer.integration.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.commonservice.common_core.kafka.event.CommonEventPayload;
import org.hvtra.commonservice.common_core.kafka.event.DebeziumOutboxEventPayloadParser;
import org.hvtra.commonservice.common_core.kafka.event.EventMetadataResolver;
import org.hvtra.commonservice.common_core.kafka.inbox.InboxStatus;
import org.hvtra.customer.model.entity.CustomerInbox;
import org.hvtra.customer.repository.CustomerInboxRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Nhận event đơn hàng từ outbox của Order-Service một cách idempotent.
 *
 * <p>Inbox và nghiệp vụ nằm chung một transaction: xử lý lỗi thì cả hai rollback và exception được ném lại
 * để Kafka gửi lại; xử lý xong thì inbox ở PROCESSED / IGNORED và lần nhận trùng sau sẽ bị bỏ qua.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventInboxService {

    private static final String SOURCE_SERVICE = "order-service";
    private static final String UNKNOWN_EVENT = "UNKNOWN_ORDER_EVENT";

    private final DebeziumOutboxEventPayloadParser payloadParser;
    private final EventMetadataResolver metadataResolver;
    private final CustomerInboxRepository inboxRepository;

    @Value("${kafka-consumer-config.group-id:customer-service-order-events}")
    private String consumerGroup;

    /**
     * @param expectedEventType loại event listener này xử lý; event loại khác trên cùng topic bị đánh dấu IGNORED
     * @param handler           xử lý nghiệp vụ; trả false nếu event hợp lệ nhưng không cần làm gì
     */
    @Transactional
    public <T extends CommonEventPayload> void handle(
            ConsumerRecord<String, String> record,
            Class<T> payloadType,
            String expectedEventType,
            Function<T, String> entityIdResolver,
            Predicate<T> handler
    ) {
        Optional<T> parsed = payloadParser.parseCreateEvent(record.value(), payloadType, expectedEventType, null);
        if (parsed.isEmpty()) {
            log.debug("Bỏ qua record không phải outbox create. topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset());
            return;
        }

        T event = parsed.get();
        metadataResolver.normalizeBeforeEventId(record, event, topic -> expectedEventType, UNKNOWN_EVENT, expectedEventType);
        metadataResolver.normalizeEventKey(record, event);
        String eventId = metadataResolver.resolveEventId(record, event, expectedEventType);

        Optional<CustomerInbox> existing = inboxRepository.findByEventIdAndConsumerGroup(eventId, consumerGroup);
        if (existing.isPresent()) {
            log.debug("Event {} đã xử lý (status={}), bỏ qua", eventId, existing.get().getStatus());
            return;
        }

        CustomerInbox inbox = inboxRepository.save(buildInbox(record, event, eventId, entityIdResolver.apply(event)));

        boolean processed = expectedEventType.equals(event.getEventType()) && handler.test(event);
        inbox.setStatus(processed ? InboxStatus.PROCESSED : InboxStatus.IGNORED);
        inbox.setProcessedAt(Instant.now());
        inboxRepository.save(inbox);
    }

    private CustomerInbox buildInbox(
            ConsumerRecord<String, String> record,
            CommonEventPayload event,
            String eventId,
            String entityId
    ) {
        CustomerInbox inbox = new CustomerInbox();
        inbox.setEventId(eventId);
        inbox.setConsumerGroup(consumerGroup);
        inbox.setTopic(record.topic());
        inbox.setPartitionNo(record.partition());
        inbox.setOffsetNo(record.offset());
        inbox.setMessageKey(record.key());
        inbox.setEventType(event.getEventType());
        inbox.setEventVersion(1);
        inbox.setSourceService(metadataResolver.sourceService(record, SOURCE_SERVICE));
        inbox.setEntityType("ORDER");
        inbox.setEntityId(entityId);
        inbox.setCorrelationId(metadataResolver.correlationId(record, entityId));
        inbox.setPayloadJson(record.value());
        inbox.setHeadersJson(metadataResolver.serializeHeaders(record));
        inbox.setStatus(InboxStatus.PROCESSING);
        return inbox;
    }
}
