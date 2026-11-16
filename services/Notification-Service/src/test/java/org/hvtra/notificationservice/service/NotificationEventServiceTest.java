package org.hvtra.notificationservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.commonservice.common_core.kafka.event.DebeziumOutboxEventPayloadParser;
import org.hvtra.commonservice.common_core.kafka.event.EventMetadataResolver;
import org.hvtra.commonservice.common_core.kafka.inbox.InboxStatus;
import org.hvtra.notificationservice.domain.entity.Notification;
import org.hvtra.notificationservice.domain.entity.NotificationInbox;
import org.hvtra.notificationservice.dto.event.OrderNotificationEventPayload;
import org.hvtra.notificationservice.dto.response.NotificationResponse;
import org.hvtra.notificationservice.integration.consumer.OrderNotificationEventMetadataResolver;
import org.hvtra.notificationservice.integration.consumer.OrderNotificationEventPayloadParser;
import org.hvtra.notificationservice.invoice.InvoiceEmailService;
import org.hvtra.notificationservice.repository.NotificationInboxRepository;
import org.hvtra.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit test: repositories, SSE and invoice mail are mocks; the payload parser and metadata
 * resolver are the real ones so the Kafka record is interpreted exactly as in production.
 */
@ExtendWith(MockitoExtension.class)
class NotificationEventServiceTest {

    private static final String GROUP = "fixture-consumer-group";
    private static final String TOPIC = "notification.order.payment-succeeded";
    private static final UUID NOTIFICATION_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationInboxRepository inboxRepository;
    @Mock
    private NotificationSseService sseService;
    @Mock
    private InvoiceEmailService invoiceEmailService;

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    private NotificationEventService service;

    @BeforeEach
    void setUp() {
        service = new NotificationEventService(
                notificationRepository,
                inboxRepository,
                new OrderNotificationEventPayloadParser(new DebeziumOutboxEventPayloadParser(objectMapper)),
                new OrderNotificationEventMetadataResolver(new EventMetadataResolver(objectMapper)),
                sseService,
                invoiceEmailService
        );
        ReflectionTestUtils.setField(service, "consumerGroup", GROUP);

        lenient().when(inboxRepository.saveAndFlush(any(NotificationInbox.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(notificationRepository.saveAndFlush(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId(NOTIFICATION_ID);
            return notification;
        });
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ---------------------------------------------------------------- fixtures

    private Map<String, Object> payload(String eventId, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (eventId != null) {
            payload.put("eventId", eventId);
        }
        if (eventType != null) {
            payload.put("eventType", eventType);
        }
        payload.put("orderId", "order-1");
        payload.put("orderCode", "POS-1");
        payload.put("returnId", "return-1");
        payload.put("returnCode", "RT-1");
        payload.put("userId", "user-1");
        payload.put("customerId", "customer-1");
        payload.put("paymentId", "payment-1");
        payload.put("sagaId", "saga-1");
        payload.put("correlationId", "corr-1");
        payload.put("newStatus", "SHIPPING");
        return payload;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private ConsumerRecord<String, String> record(String topic, String key, String value) {
        return new ConsumerRecord<>(topic, 2, 15L, key, value);
    }

    private ConsumerRecord<String, String> record(Map<String, Object> payload) {
        return record(TOPIC, "order-1", json(payload));
    }

    private void header(ConsumerRecord<String, String> record, String name, String value) {
        record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
    }

    private NotificationInbox savedInbox() {
        ArgumentCaptor<NotificationInbox> captor = ArgumentCaptor.forClass(NotificationInbox.class);
        verify(inboxRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private Notification savedNotification() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private NotificationInbox existingInbox(String eventId, InboxStatus status) {
        NotificationInbox inbox = new NotificationInbox();
        inbox.setEventId(eventId);
        inbox.setConsumerGroup(GROUP);
        inbox.setStatus(status);
        return inbox;
    }

    // ---------------------------------------------------------------- happy path

    @Test
    void notificationEventIsStoredPushedAndInboxMarkedProcessed() {
        ConsumerRecord<String, String> record = record(payload("evt-1", "ORDER_PAYMENT_SUCCEEDED"));

        service.handleOrderEvent(record);

        Notification notification = savedNotification();
        assertThat(notification.getUserId()).isEqualTo("user-1");
        assertThat(notification.getCustomerId()).isEqualTo("customer-1");
        assertThat(notification.getType()).isEqualTo("ORDER_PAYMENT_SUCCEEDED");
        assertThat(notification.getSourceEventType()).isEqualTo("ORDER_PAYMENT_SUCCEEDED");
        assertThat(notification.getSourceEventId()).isEqualTo("evt-1");
        assertThat(notification.getTitle()).isEqualTo("Thanh toán thành công");
        assertThat(notification.getMessage()).isEqualTo("Đơn POS-1 đã nhận thanh toán.");
        assertThat(notification.getAggregateType()).isEqualTo("ORDER");
        assertThat(notification.getAggregateId()).isEqualTo("order-1");
        assertThat(notification.getOrderId()).isEqualTo("order-1");
        assertThat(notification.getOrderCode()).isEqualTo("POS-1");
        assertThat(notification.getPaymentId()).isEqualTo("payment-1");
        assertThat(notification.getSagaId()).isEqualTo("saga-1");
        assertThat(notification.getStatus()).isEqualTo("UNREAD");
        assertThat(notification.getReadAt()).isNull();

        NotificationInbox inbox = savedInbox();
        assertThat(inbox.getStatus()).isEqualTo(InboxStatus.PROCESSED);
        assertThat(inbox.getProcessedAt()).isNotNull();
        assertThat(inbox.getLastError()).isNull();

        ArgumentCaptor<NotificationResponse> pushed = ArgumentCaptor.forClass(NotificationResponse.class);
        verify(sseService).sendToUser(eq("user-1"), pushed.capture());
        assertThat(pushed.getValue().id()).isEqualTo(NOTIFICATION_ID);
        assertThat(pushed.getValue().title()).isEqualTo("Thanh toán thành công");
        assertThat(pushed.getValue().status()).isEqualTo("UNREAD");

        ArgumentCaptor<OrderNotificationEventPayload> mailed = ArgumentCaptor.forClass(OrderNotificationEventPayload.class);
        verify(invoiceEmailService).sendAfterCommit(mailed.capture());
        assertThat(mailed.getValue().getOrderCode()).isEqualTo("POS-1");
    }

    @Test
    void inboxRowRecordsKafkaCoordinatesAndEventMetadata() {
        String raw = json(payload("evt-1", "ORDER_PAYMENT_SUCCEEDED"));
        ConsumerRecord<String, String> record = record(TOPIC, "order-1", raw);
        header(record, "source_service", "payment-service");
        header(record, "aggregate_type", "PAYMENT");
        header(record, "aggregate_id", "aggregate-9");
        header(record, "correlation_id", "corr-header");

        service.handleOrderEvent(record);

        NotificationInbox inbox = savedInbox();
        assertThat(inbox.getEventId()).isEqualTo("evt-1");
        assertThat(inbox.getConsumerGroup()).isEqualTo(GROUP);
        assertThat(inbox.getTopic()).isEqualTo(TOPIC);
        assertThat(inbox.getPartitionNo()).isEqualTo(2);
        assertThat(inbox.getOffsetNo()).isEqualTo(15L);
        assertThat(inbox.getMessageKey()).isEqualTo("order-1");
        assertThat(inbox.getEventType()).isEqualTo("ORDER_PAYMENT_SUCCEEDED");
        assertThat(inbox.getEventVersion()).isEqualTo(1);
        assertThat(inbox.getSourceService()).isEqualTo("payment-service");
        assertThat(inbox.getEntityType()).isEqualTo("PAYMENT");
        assertThat(inbox.getEntityId()).isEqualTo("aggregate-9");
        assertThat(inbox.getSagaId()).isEqualTo("saga-1");
        assertThat(inbox.getCorrelationId()).isEqualTo("corr-header");
        assertThat(inbox.getPayloadJson()).isEqualTo(raw);
        assertThat(inbox.getHeadersJson()).contains("\"source_service\":\"payment-service\"");

        Notification notification = savedNotification();
        assertThat(notification.getAggregateType()).isEqualTo("PAYMENT");
        assertThat(notification.getAggregateId()).isEqualTo("aggregate-9");
    }

    @Test
    void inboxMetadataFallsBackToOrderDefaultsWithoutHeaders() {
        service.handleOrderEvent(record(payload("evt-1", "ORDER_CANCELLED")));

        NotificationInbox inbox = savedInbox();
        assertThat(inbox.getSourceService()).isEqualTo("order-service");
        assertThat(inbox.getEntityType()).isEqualTo("ORDER");
        assertThat(inbox.getEntityId()).isEqualTo("order-1");
        assertThat(inbox.getCorrelationId()).isEqualTo("corr-1");
        assertThat(inbox.getHeadersJson()).isEqualTo("{}");
    }

    // ---------------------------------------------------------------- title / message

    @Test
    void titleAndMessageFromProducerWin() {
        Map<String, Object> payload = payload("evt-1", "ORDER_CANCELLED");
        payload.put("title", "Tiêu đề riêng");
        payload.put("message", "Nội dung riêng");

        service.handleOrderEvent(record(payload));

        Notification notification = savedNotification();
        assertThat(notification.getTitle()).isEqualTo("Tiêu đề riêng");
        assertThat(notification.getMessage()).isEqualTo("Nội dung riêng");
    }

    @Test
    void blankTitleAndMessageFallBackToDefaults() {
        Map<String, Object> payload = payload("evt-1", "ORDER_CANCELLED");
        payload.put("title", "   ");
        payload.put("message", "");

        service.handleOrderEvent(record(payload));

        Notification notification = savedNotification();
        assertThat(notification.getTitle()).isEqualTo("Đơn đã huỷ");
        assertThat(notification.getMessage()).isEqualTo("Đơn POS-1 đã bị huỷ.");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ORDER_PAYMENT_SUCCEEDED   | Thanh toán thành công       | Đơn POS-1 đã nhận thanh toán.",
            "ORDER_CONFIRMED           | Thanh toán thành công       | Đơn POS-1 đã nhận thanh toán.",
            "ORDER_PAYMENT_FAILED      | Thanh toán chưa hoàn tất    | Đơn POS-1 chưa thanh toán được.",
            "ORDER_PAYMENT_NEED_REVIEW | Thanh toán cần kiểm tra     | Giao dịch của đơn POS-1 cần kiểm tra thủ công.",
            "ORDER_CANCELLED           | Đơn đã huỷ                  | Đơn POS-1 đã bị huỷ.",
            "ORDER_STATUS_CHANGED      | Order status changed        | Order POS-1 status changed to SHIPPING.",
            "RETURN_CREATED            | Phiếu hoàn hàng đã được tạo | Phiếu hoàn hàng RT-1 đã được tạo.",
            "RETURN_RECEIVED           | Đã nhận hàng trả            | Cửa hàng đã nhận hàng trả của phiếu RT-1.",
            "RETURN_CANCELLED          | Phiếu hoàn hàng đã bị hủy   | Phiếu hoàn hàng RT-1 đã bị hủy.",
            "RETURN_REFUND_COMPLETED   | Hoàn tiền thành công        | Phiếu RT-1 đã hoàn tiền thành công.",
            "RETURN_COMPLETED          | Xử lý hàng trả hoàn tất     | Phiếu hoàn RT-1 đã hoàn tất xử lý hàng. Không phát sinh hoàn tiền."
    })
    void defaultTitleAndMessagePerEventType(String eventType, String title, String message) {
        service.handleOrderEvent(record(payload("evt-1", eventType)));

        Notification notification = savedNotification();
        assertThat(notification.getType()).isEqualTo(eventType);
        assertThat(notification.getTitle()).isEqualTo(title);
        assertThat(notification.getMessage()).isEqualTo(message);
        assertThat(savedInbox().getStatus()).isEqualTo(InboxStatus.PROCESSED);
    }

    @Test
    void completedOrderGetsNonEmptyTextMentioningTheOrder() {
        service.handleOrderEvent(record(payload("evt-1", "ORDER_COMPLETED")));

        Notification notification = savedNotification();
        assertThat(notification.getTitle()).isNotBlank();
        assertThat(notification.getMessage()).contains("POS-1");
    }

    @Test
    void messageFallsBackToIdsWhenCodesAreMissing() {
        Map<String, Object> order = payload("evt-order", "ORDER_PAYMENT_FAILED");
        order.remove("orderCode");
        Map<String, Object> giveBack = payload("evt-return", "RETURN_CREATED");
        giveBack.put("returnCode", " ");

        service.handleOrderEvent(record(order));
        service.handleOrderEvent(record(giveBack));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).saveAndFlush(captor.capture());
        List<Notification> saved = captor.getAllValues();
        assertThat(saved.get(0).getMessage()).isEqualTo("Đơn order-1 chưa thanh toán được.");
        assertThat(saved.get(1).getMessage()).isEqualTo("Phiếu hoàn hàng return-1 đã được tạo.");
    }

    // ---------------------------------------------------------------- event type resolution

    @Test
    void unknownEventTypeIsRecordedAsIgnoredWithoutNotification() {
        service.handleOrderEvent(record(payload("evt-1", "INVENTORY_ADJUSTED")));

        NotificationInbox inbox = savedInbox();
        assertThat(inbox.getEventType()).isEqualTo("INVENTORY_ADJUSTED");
        assertThat(inbox.getStatus()).isEqualTo(InboxStatus.IGNORED);
        assertThat(inbox.getProcessedAt()).isNotNull();
        verifyNoInteractions(notificationRepository, sseService, invoiceEmailService);
    }

    @Test
    void eventTypeIsCaseSensitive() {
        service.handleOrderEvent(record(payload("evt-1", "order_cancelled")));

        assertThat(savedInbox().getStatus()).isEqualTo(InboxStatus.IGNORED);
        verifyNoInteractions(notificationRepository, sseService);
    }

    @Test
    void missingEventTypeIsDerivedFromOrderEventTopic() {
        service.handleOrderEvent(record("order.event.ORDER_CONFIRMED", "order-1", json(payload("evt-1", null))));

        assertThat(savedNotification().getType()).isEqualTo("ORDER_CONFIRMED");
        assertThat(savedInbox().getEventType()).isEqualTo("ORDER_CONFIRMED");
    }

    @Test
    void eventTypeHeaderBeatsTopicButNotPayload() {
        ConsumerRecord<String, String> fromHeader =
                record("order.event.ORDER_CONFIRMED", "order-1", json(payload("evt-header", null)));
        header(fromHeader, "event_type", "ORDER_CANCELLED");
        ConsumerRecord<String, String> fromPayload =
                record("order.event.ORDER_CONFIRMED", "order-1", json(payload("evt-payload", "RETURN_CREATED")));
        header(fromPayload, "event_type", "ORDER_CANCELLED");

        service.handleOrderEvent(fromHeader);
        service.handleOrderEvent(fromPayload);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues()).extracting(Notification::getType)
                .containsExactly("ORDER_CANCELLED", "RETURN_CREATED");
    }

    @Test
    void eventWithoutAnyTypeOnNonOrderTopicIsIgnoredAsUnknown() {
        service.handleOrderEvent(record(payload("evt-1", null)));

        NotificationInbox inbox = savedInbox();
        assertThat(inbox.getEventType()).isEqualTo("UNKNOWN_ORDER_EVENT");
        assertThat(inbox.getStatus()).isEqualTo(InboxStatus.IGNORED);
        verifyNoInteractions(notificationRepository, sseService, invoiceEmailService);
    }

    // ---------------------------------------------------------------- event id resolution

    @Test
    void eventIdHeaderBeatsPayloadEventId() {
        ConsumerRecord<String, String> record = record(payload("evt-payload", "ORDER_CANCELLED"));
        header(record, "event_id", "evt-header");

        service.handleOrderEvent(record);

        verify(inboxRepository).findByEventIdAndConsumerGroup("evt-header", GROUP);
        assertThat(savedInbox().getEventId()).isEqualTo("evt-header");
        assertThat(savedNotification().getSourceEventId()).isEqualTo("evt-header");
    }

    @Test
    void missingEventIdFallsBackToRecordKey() {
        service.handleOrderEvent(record(TOPIC, "legacy-key-7", json(payload(null, "ORDER_CANCELLED"))));

        assertThat(savedInbox().getEventId()).isEqualTo("legacy-key-7");
        assertThat(savedNotification().getSourceEventId()).isEqualTo("legacy-key-7");
    }

    @Test
    void missingEventIdAndKeyFallsBackToKafkaCoordinates() {
        service.handleOrderEvent(record(TOPIC, null, json(payload(null, "ORDER_CANCELLED"))));

        assertThat(savedInbox().getEventId()).isEqualTo(TOPIC + "-2-15");
    }

    // ---------------------------------------------------------------- idempotency

    @ParameterizedTest
    @EnumSource(value = InboxStatus.class, names = {"PROCESSED", "IGNORED", "DEAD"})
    void duplicateOfFinishedEventIsSkippedWithoutSideEffects(InboxStatus status) {
        NotificationInbox existing = existingInbox("evt-1", status);
        when(inboxRepository.findByEventIdAndConsumerGroup("evt-1", GROUP)).thenReturn(Optional.of(existing));

        service.handleOrderEvent(record(payload("evt-1", "ORDER_CANCELLED")));

        assertThat(existing.getStatus()).isEqualTo(status);
        verify(inboxRepository, never()).saveAndFlush(any());
        verifyNoInteractions(notificationRepository, sseService, invoiceEmailService);
    }

    @ParameterizedTest
    @EnumSource(value = InboxStatus.class, names = {"RECEIVED", "PROCESSING"})
    void duplicateOfEventStillInFlightFailsSoKafkaRedelivers(InboxStatus status) {
        when(inboxRepository.findByEventIdAndConsumerGroup("evt-1", GROUP))
                .thenReturn(Optional.of(existingInbox("evt-1", status)));

        assertThatThrownBy(() -> service.handleOrderEvent(record(payload("evt-1", "ORDER_CANCELLED"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("evt-1")
                .hasMessageContaining(status.name());

        verify(inboxRepository, never()).saveAndFlush(any());
        verifyNoInteractions(notificationRepository, sseService, invoiceEmailService);
    }

    @Test
    void concurrentInsertOfSameEventUsesTheWinnersFinishedInboxRow() {
        when(inboxRepository.findByEventIdAndConsumerGroup("evt-1", GROUP))
                .thenReturn(Optional.empty(), Optional.of(existingInbox("evt-1", InboxStatus.PROCESSED)));
        when(inboxRepository.saveAndFlush(any(NotificationInbox.class)))
                .thenThrow(new DataIntegrityViolationException("uk_notification_inbox_event_consumer"));

        assertThatCode(() -> service.handleOrderEvent(record(payload("evt-1", "ORDER_CANCELLED"))))
                .doesNotThrowAnyException();

        verifyNoInteractions(notificationRepository, sseService, invoiceEmailService);
    }

    @Test
    void uniqueViolationWithoutMatchingInboxRowIsRethrown() {
        DataIntegrityViolationException violation = new DataIntegrityViolationException("other constraint");
        when(inboxRepository.saveAndFlush(any(NotificationInbox.class))).thenThrow(violation);

        assertThatThrownBy(() -> service.handleOrderEvent(record(payload("evt-1", "ORDER_CANCELLED"))))
                .isSameAs(violation);

        verifyNoInteractions(notificationRepository, sseService, invoiceEmailService);
    }

    @Test
    void existingNotificationForSameSourceEventIsNotDuplicatedOrPushedAgain() {
        when(notificationRepository.findBySourceEventId("evt-1")).thenReturn(Optional.of(new Notification()));

        service.handleOrderEvent(record(payload("evt-1", "ORDER_CANCELLED")));

        verify(notificationRepository, never()).saveAndFlush(any());
        verifyNoInteractions(sseService);
        assertThat(savedInbox().getStatus()).isEqualTo(InboxStatus.PROCESSED);
    }

    @Test
    @Disabled("Suspected bug: FAILED inbox rows are treated as terminal, so an event whose processing "
            + "failed is acked and never retried on redelivery (NotificationEventService.java:248-253)")
    void redeliveryOfFailedEventIsProcessedAgain() {
        NotificationInbox failed = existingInbox("evt-1", InboxStatus.FAILED);
        failed.setLastError("db down");
        when(inboxRepository.findByEventIdAndConsumerGroup("evt-1", GROUP)).thenReturn(Optional.of(failed));

        service.handleOrderEvent(record(payload("evt-1", "ORDER_CANCELLED")));

        assertThat(savedNotification().getSourceEventId()).isEqualTo("evt-1");
        assertThat(failed.getStatus()).isEqualTo(InboxStatus.PROCESSED);
        assertThat(failed.getLastError()).isNull();
    }

    // ---------------------------------------------------------------- recipients / push

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void notificationWithoutRecipientUserIsStoredButNotPushed(String userId) {
        Map<String, Object> payload = payload("evt-1", "ORDER_CANCELLED");
        payload.put("userId", userId);

        service.handleOrderEvent(record(payload));

        assertThat(savedNotification().getCustomerId()).isEqualTo("customer-1");
        assertThat(savedInbox().getStatus()).isEqualTo(InboxStatus.PROCESSED);
        verifyNoInteractions(sseService);
    }

    @Test
    void pushIsDeferredUntilTransactionCommit() {
        TransactionSynchronizationManager.initSynchronization();

        service.handleOrderEvent(record(payload("evt-1", "ORDER_CANCELLED")));

        verifyNoInteractions(sseService);
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);

        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(sseService).sendToUser(eq("user-1"), any(NotificationResponse.class));
    }

    // ---------------------------------------------------------------- failure paths

    @Test
    void processingFailureIsRecordedOnInboxAndNotPropagated() {
        when(notificationRepository.saveAndFlush(any(Notification.class)))
                .thenThrow(new IllegalStateException("Synthetic database outage"));

        assertThatCode(() -> service.handleOrderEvent(record(payload("evt-1", "ORDER_CANCELLED"))))
                .doesNotThrowAnyException();

        NotificationInbox inbox = savedInbox();
        assertThat(inbox.getStatus()).isEqualTo(InboxStatus.FAILED);
        assertThat(inbox.getLastError()).isEqualTo("Synthetic database outage");
        assertThat(inbox.getProcessedAt()).isNotNull();
        verifyNoInteractions(sseService, invoiceEmailService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{not json", "[1,2]", "{\"occurredAt\":\"not-a-date\"}", "{\"items\":\"not-a-list\"}"})
    void malformedPayloadIsRejectedBeforeAnythingIsStored(String raw) {
        assertThatThrownBy(() -> service.handleOrderEvent(record(TOPIC, "order-1", raw)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(inboxRepository, notificationRepository, sseService, invoiceEmailService);
    }

    // ---------------------------------------------------------------- CDC envelopes

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "null"})
    void tombstoneIsSkipped(String raw) {
        service.handleOrderEvent(record(TOPIC, "order-1", raw));

        verifyNoInteractions(inboxRepository, notificationRepository, sseService, invoiceEmailService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"u", "d", "t"})
    void debeziumChangesThatAreNotOutboxInsertsAreSkipped(String op) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("op", op);
        envelope.put("after", Map.of("event_id", "evt-cdc", "payload", json(payload(null, "ORDER_CANCELLED"))));

        service.handleOrderEvent(record(TOPIC, "order-1", json(envelope)));

        verifyNoInteractions(inboxRepository, notificationRepository, sseService, invoiceEmailService);
    }

    @Test
    void debeziumInsertIsHydratedFromOutboxRowColumns() {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("event_id", "evt-cdc");
        after.put("event_type", "ORDER_CANCELLED");
        after.put("aggregate_id", "order-7");
        after.put("saga_id", "saga-7");
        after.put("correlation_id", "corr-7");
        after.put("payload", "{\"orderCode\":\"POS-7\",\"userId\":\"user-7\"}");
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("op", "c");
        envelope.put("after", after);

        service.handleOrderEvent(record(TOPIC, "order-7", json(envelope)));

        Notification notification = savedNotification();
        assertThat(notification.getSourceEventId()).isEqualTo("evt-cdc");
        assertThat(notification.getType()).isEqualTo("ORDER_CANCELLED");
        assertThat(notification.getOrderId()).isEqualTo("order-7");
        assertThat(notification.getSagaId()).isEqualTo("saga-7");
        assertThat(notification.getMessage()).isEqualTo("Đơn POS-7 đã bị huỷ.");
        NotificationInbox inbox = savedInbox();
        assertThat(inbox.getCorrelationId()).isEqualTo("corr-7");
        assertThat(inbox.getEntityId()).isEqualTo("order-7");
        verify(sseService).sendToUser(eq("user-7"), any(NotificationResponse.class));
    }
}
