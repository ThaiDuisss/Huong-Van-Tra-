package org.hvtra.notificationservice.integration.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.notificationservice.service.NotificationEventService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class OrderNotificationEventConsumerTest {

    private final NotificationEventService eventService = mock(NotificationEventService.class);
    private final Acknowledgment acknowledgment = mock(Acknowledgment.class);
    private final OrderNotificationEventConsumer consumer = new OrderNotificationEventConsumer(eventService);
    private final ConsumerRecord<String, String> record =
            new ConsumerRecord<>("order.completed", 0, 1L, "order-1", "{}");

    @Test
    void offsetIsAcknowledgedOnlyAfterEventWasHandled() {
        consumer.handle(record, acknowledgment);

        InOrder order = inOrder(eventService, acknowledgment);
        order.verify(eventService).handleOrderEvent(record);
        order.verify(acknowledgment).acknowledge();
    }

    @Test
    void offsetIsNotAcknowledgedWhenHandlingFails() {
        IllegalStateException failure = new IllegalStateException("Synthetic in-flight duplicate");
        doThrow(failure).when(eventService).handleOrderEvent(record);

        assertThatThrownBy(() -> consumer.handle(record, acknowledgment)).isSameAs(failure);

        verifyNoInteractions(acknowledgment);
    }
}
