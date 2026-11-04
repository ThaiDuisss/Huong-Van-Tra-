package org.hvtra.orderservice.integration.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentSucceededConsumerTest {
    @Mock private PaymentSucceededInboxService inbox;
    @Mock private Acknowledgment acknowledgment;
    @InjectMocks private PaymentSucceededConsumer consumer;
    private final ConsumerRecord<String, String> record = new ConsumerRecord<>("payment.succeeded", 0, 4L, "key", "{}");

    @Test
    void acknowledgesOnlyAfterInboxHandlingSucceeds() {
        consumer.handle(record, acknowledgment);
        var sequence = inOrder(inbox, acknowledgment);
        sequence.verify(inbox).handle(record);
        sequence.verify(acknowledgment).acknowledge();
        sequence.verifyNoMoreInteractions();
    }

    @Test
    void failedInboxProcessingDoesNotCommitOffset() {
        RuntimeException failure = new IllegalStateException("persistence failed");
        doThrow(failure).when(inbox).handle(record);
        assertSame(failure, assertThrows(RuntimeException.class, () -> consumer.handle(record, acknowledgment)));
        verifyNoInteractions(acknowledgment);
    }

    @Test
    void failedAcknowledgementPropagatesWithoutReinvokingInboxInSameCall() {
        RuntimeException failure = new IllegalStateException("ack failed");
        doThrow(failure).when(acknowledgment).acknowledge();
        assertSame(failure, assertThrows(RuntimeException.class, () -> consumer.handle(record, acknowledgment)));
        verify(inbox).handle(record);
        verify(acknowledgment).acknowledge();
    }
}
