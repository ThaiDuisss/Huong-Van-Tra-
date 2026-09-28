package org.hvtra.orderservice.integration.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.commonservice.common_core.kafka.inbox.InboxStatus;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderInbox;
import org.hvtra.orderservice.domain.enums.OrderInventoryProcessingStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.event.PaymentOrderEventPayload;
import org.hvtra.orderservice.repository.OrderInboxRepository;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentSucceededInboxServiceTest {

    @Mock private OrderService orderService;
    @Mock private OrderInboxRepository inboxRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentEventPayloadParser parser;
    @Mock private PaymentEventMetadataResolver metadataResolver;

    private PaymentSucceededInboxService service;

    @BeforeEach
    void setUp() {
        service = new PaymentSucceededInboxService(orderService, inboxRepository, orderRepository,
                parser, metadataResolver);
    }

    @Test
    void processedSuccessEventRetriesPaidOrderWithFailedInventory() {
        UUID orderId = UUID.randomUUID();
        PaymentOrderEventPayload event = event(orderId);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("payments", 0, 1, "key", "payload");
        OrderInbox inbox = new OrderInbox();
        inbox.setStatus(InboxStatus.PROCESSED);
        Order order = new Order();
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setInventoryProcessingStatus(OrderInventoryProcessingStatus.FAILED);
        when(parser.parseCreateEvent("payload")).thenReturn(Optional.of(event));
        when(metadataResolver.resolveEventId(record, event)).thenReturn("evt-1");
        when(inboxRepository.findByEventIdAndConsumerGroup("evt-1", null)).thenReturn(Optional.of(inbox));
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));

        service.handle(record);

        verify(orderService).handlePaymentEvent(event);
    }

    @Test
    void processedSuccessEventDoesNotRepeatConsumedInventory() {
        UUID orderId = UUID.randomUUID();
        PaymentOrderEventPayload event = event(orderId);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("payments", 0, 1, "key", "payload");
        OrderInbox inbox = new OrderInbox();
        inbox.setStatus(InboxStatus.PROCESSED);
        Order order = new Order();
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setInventoryProcessingStatus(OrderInventoryProcessingStatus.CONSUMED);
        when(parser.parseCreateEvent("payload")).thenReturn(Optional.of(event));
        when(metadataResolver.resolveEventId(record, event)).thenReturn("evt-1");
        when(inboxRepository.findByEventIdAndConsumerGroup("evt-1", null)).thenReturn(Optional.of(inbox));
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));

        service.handle(record);

        verify(orderService, never()).handlePaymentEvent(any());
    }

    private PaymentOrderEventPayload event(UUID orderId) {
        PaymentOrderEventPayload event = new PaymentOrderEventPayload();
        event.setOrderId(orderId.toString());
        event.setEventType("PAYMENT_SUCCEEDED");
        return event;
    }
}
