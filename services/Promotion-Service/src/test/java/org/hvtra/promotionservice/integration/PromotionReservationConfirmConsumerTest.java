package org.hvtra.promotionservice.integration;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.promotionservice.integration.consumer.PromotionReservationConfirmConsumer;
import org.hvtra.promotionservice.integration.consumer.PromotionReservationConfirmInboxService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("PromotionReservationConfirmConsumer")
class PromotionReservationConfirmConsumerTest {

    @Mock
    PromotionReservationConfirmInboxService inboxService;

    @Mock
    Acknowledgment acknowledgment;

    @InjectMocks
    PromotionReservationConfirmConsumer consumer;

    final ConsumerRecord<String, String> record =
            new ConsumerRecord<>("promotion.usage-commit.requested", 0, 1L, "key", "{}");

    @Test
    @DisplayName("acknowledges the record only after the inbox service handled it")
    void handle_acknowledgesAfterProcessing() {
        consumer.handle(record, acknowledgment);

        InOrder order = inOrder(inboxService, acknowledgment);
        order.verify(inboxService).handle(record);
        order.verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("does not acknowledge when processing fails, so Kafka redelivers the record")
    void handle_failure_notAcknowledged() {
        IllegalStateException failure = new IllegalStateException("boom");
        doThrow(failure).when(inboxService).handle(record);

        assertThatThrownBy(() -> consumer.handle(record, acknowledgment)).isSameAs(failure);
        verifyNoInteractions(acknowledgment);
    }
}
